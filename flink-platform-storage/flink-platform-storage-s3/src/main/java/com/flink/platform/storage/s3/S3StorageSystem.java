package com.flink.platform.storage.s3;

import com.flink.platform.common.util.Preconditions;
import com.flink.platform.storage.StorageProperties;
import com.flink.platform.storage.base.StorageStatus;
import com.flink.platform.storage.base.StorageSystem;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;

import static com.flink.platform.common.constants.Constant.GLOBAL_ZONE_ID;
import static com.flink.platform.common.constants.Constant.SLASH;
import static com.flink.platform.common.util.StringUtil.stripLeadingSlash;
import static com.flink.platform.common.util.StringUtil.stripTrailingSlash;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * S3 storage system. Path convention is {@code s3://bucket/key}; {@code s3a://} is also accepted
 * for compatibility with Hadoop-style URIs.
 */
@Slf4j
public class S3StorageSystem implements StorageSystem {

    private static final String S3_SCHEME_PREFIX = "s3://";
    private static final String S3A_SCHEME_PREFIX = "s3a://";

    private final StorageProperties properties;

    private S3Client s3;
    private String rootPath;

    public S3StorageSystem(StorageProperties properties) {
        this.properties = Preconditions.checkNotNull(properties);
    }

    @Override
    public String getRootPath() {
        return rootPath;
    }

    @Override
    public boolean isDistributed() {
        return true;
    }

    @Override
    public String getFileSeparator() {
        return SLASH;
    }

    @Override
    public String getParentPath(String filePath) {
        var trimmed = stripTrailingSlash(filePath);
        var idx = trimmed.lastIndexOf(SLASH);
        if (idx < 0) {
            return trimmed;
        }
        return trimmed.substring(0, idx);
    }

    @Override
    public StorageStatus getFileStatus(String filePath) throws IOException {
        try {
            var s3Location = parse(filePath);
            var head = s3.headObject(HeadObjectRequest.builder()
                    .bucket(s3Location.bucket)
                    .key(s3Location.key)
                    .build());
            var modTime = LocalDateTime.ofInstant(head.lastModified(), GLOBAL_ZONE_ID);
            return StorageStatus.of(head.contentLength(), modTime);
        } catch (S3Exception e) {
            throw new IOException("S3 object not found or head failed: " + filePath, e);
        }
    }

    @Override
    public void copyToLocalFile(String srcFile, String dstFile) throws IOException {
        var s3Location = parse(srcFile);
        var dstPath = Paths.get(dstFile);
        var parent = dstPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        s3.getObject(
                GetObjectRequest.builder()
                        .bucket(s3Location.bucket)
                        .key(s3Location.key)
                        .build(),
                dstPath);
    }

    @Override
    public void copyToLocalFileIfChanged(String s3File, String localFile) throws IOException {
        var localPath = Paths.get(localFile);
        boolean isCopy = true;
        if (Files.exists(localPath)) {
            var localSize = Files.size(localPath);
            var localMtime = Files.getLastModifiedTime(localPath).toMillis();
            var remote = getFileStatusRaw(s3File);
            var remoteMtime = remote.lastModified().toEpochMilli();
            isCopy = localSize != remote.contentLength() || localMtime < remoteMtime;
        }
        if (isCopy) {
            copyToLocalFile(s3File, localFile);
        }
    }

    @Override
    public void copyFromLocalFile(String srcFile, String dstFile, boolean delSrc, boolean overwrite)
            throws IOException {
        var s3Location = parse(dstFile);
        var srcPath = Paths.get(srcFile);
        if (!overwrite && exists(dstFile)) {
            throw new IOException("destination already exists: " + dstFile);
        }
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(s3Location.bucket)
                        .key(s3Location.key)
                        .build(),
                RequestBody.fromFile(srcPath));
        if (delSrc) {
            Files.deleteIfExists(srcPath);
        }
    }

    @Override
    public void createFile(String file, String data, boolean overwrite) throws IOException {
        var s3Location = parse(file);
        if (!overwrite && exists(file)) {
            throw new IOException("destination already exists: " + file);
        }

        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(s3Location.bucket)
                        .key(s3Location.key)
                        .build(),
                RequestBody.fromString(data));
    }

    @Override
    public boolean delete(String filePath, boolean recursive) throws IOException {
        if (recursive) {
            throw new IOException("recursive delete is not supported by S3StorageSystem");
        }

        var s3Location = parse(filePath);
        if (s3Location.key.isEmpty() || s3Location.key.endsWith(SLASH)) {
            throw new IOException("refuse to delete directory-like path: " + filePath);
        }

        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Location.bucket)
                    .key(s3Location.key)
                    .build());
            return true;
        } catch (S3Exception e) {
            throw new IOException("delete failed: " + filePath, e);
        }
    }

    /**
     * S3 has no native trash.
     */
    @Override
    public boolean moveToTrash(String path) throws IOException {
        return delete(path, false);
    }

    /**
     * No-op: S3 has no directories.<br> returns true to keep callers happy.
     */
    @Override
    public boolean mkdir(String path) {
        return true;
    }

    @Override
    public boolean exists(String path) throws IOException {
        var s3Location = parse(path);
        if (s3Location.key.isEmpty() || s3Location.key.endsWith(SLASH)) {
            throw new IOException("not a file path: " + path);
        }

        try {
            s3.headObject(HeadObjectRequest.builder()
                    .bucket(s3Location.bucket)
                    .key(s3Location.key)
                    .build());
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw new IOException("exists check failed: " + path, e);
        }
    }

    @Override
    public boolean rename(String srcPath, String dstPath) throws IOException {
        var src = parse(srcPath);
        var dst = parse(dstPath);
        if (src.equals(dst)) {
            return true;
        }

        try {
            s3.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(src.bucket)
                    .sourceKey(src.key)
                    .destinationBucket(dst.bucket)
                    .destinationKey(dst.key)
                    .build());
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(src.bucket)
                    .key(src.key)
                    .build());
            return true;
        } catch (S3Exception e) {
            throw new IOException("rename failed: " + srcPath + " -> " + dstPath, e);
        }
    }

    @Override
    public String normalizePath(String path) {
        if (isAbsolutePath(path)) {
            return path;
        }
        return joinPath(rootPath, path);
    }

    @Override
    public boolean isAbsolutePath(String path) {
        return StringUtils.startsWithAny(path, S3_SCHEME_PREFIX, S3A_SCHEME_PREFIX);
    }

    // ==================================================================
    // ========================= init and close =========================
    // ==================================================================

    @Override
    public void open() {
        var props = properties.getS3Properties();
        if (isNotBlank(props.endpoint()) && !props.pathStyleAccess()) {
            throw new IllegalStateException("path-style-access must be true when endpoint is set: " + props.endpoint());
        }

        var builder = S3Client.builder()
                .region(Region.of(props.region()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.pathStyleAccess())
                        .build());

        var accessKey = props.accessKey();
        var secretKey = props.secretKey();
        builder.credentialsProvider(
                (isNotBlank(accessKey) && isNotBlank(secretKey))
                        ? StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(props.accessKey(), props.secretKey()))
                        : DefaultCredentialsProvider.builder().build());

        if (isNotBlank(props.endpoint())) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }

        this.s3 = builder.build();
        this.rootPath = stripTrailingSlash(buildRootPath(props.bucket(), properties.getBasePath()));
        log.info(
                "S3 storage initialized: region={}, endpoint={}, pathStyle={}, basePath={}",
                props.region(),
                isNotBlank(props.endpoint()) ? props.endpoint() : "<default>",
                props.pathStyleAccess(),
                rootPath);
    }

    @Override
    public void close() {
        if (s3 != null) {
            s3.close();
        }
    }

    // ==================================================================
    // ========================= helpers ================================
    // ==================================================================

    private static String buildRootPath(String bucket, String basePath) {
        return S3_SCHEME_PREFIX + bucket + SLASH + stripLeadingSlash(basePath);
    }

    private HeadObjectResponse getFileStatusRaw(String path) throws IOException {
        try {
            var s3Location = parse(path);
            return s3.headObject(HeadObjectRequest.builder()
                    .bucket(s3Location.bucket)
                    .key(s3Location.key)
                    .build());
        } catch (S3Exception e) {
            throw new IOException("S3 object not found or head failed: " + path, e);
        }
    }

    private static S3Location parse(String pathStr) {
        if (pathStr == null) {
            throw new IllegalArgumentException("path is null");
        }

        String stripped;
        if (pathStr.startsWith(S3_SCHEME_PREFIX)) {
            stripped = pathStr.substring(S3_SCHEME_PREFIX.length());
        } else if (pathStr.startsWith(S3A_SCHEME_PREFIX)) {
            stripped = pathStr.substring(S3A_SCHEME_PREFIX.length());
        } else {
            throw new IllegalArgumentException("not an S3 path: " + pathStr);
        }

        var idx = stripped.indexOf(SLASH);
        var bucket = idx < 0 ? stripped : stripped.substring(0, idx);
        var key = idx < 0 ? "" : stripped.substring(idx + 1);
        return new S3Location(bucket, key);
    }

    private static String joinPath(String base, String relative) {
        return stripTrailingSlash(base) + SLASH + stripLeadingSlash(relative);
    }

    /** Bucket + key pair derived from a {@code s3://bucket/key} URI. */
    private record S3Location(String bucket, String key) {

        private S3Location {
            if (bucket == null || bucket.isEmpty()) {
                throw new IllegalArgumentException("bucket is empty");
            }
        }
    }
}
