package com.flink.platform.web.environment;

import com.flink.platform.common.environment.EnvironmentType;
import com.flink.platform.environment.EnvironmentRegistry;
import com.flink.platform.web.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.file.Files;
import java.nio.file.Paths;

import static com.flink.platform.common.constants.Constant.SLASH;
import static com.flink.platform.common.util.S3Util.S3_SCHEME_PREFIX;
import static com.flink.platform.common.util.S3Util.toLocation;

/** S3-backed implementation of {@link EnvironmentFileAdapter}. */
@Slf4j
@Component
@Order(2)
@DependsOn("s3EnvironmentBootstrap")
public class S3FileAdapter extends EnvironmentFileAdapter<S3Client> {

    private final StorageService storageService;

    @Autowired
    public S3FileAdapter(
            @Qualifier("primaryClusterIdFilePath") String primaryClusterIdFilePath,
            EnvironmentRegistry registry,
            StorageService storageService) {
        super(registry, primaryClusterIdFilePath);
        this.storageService = storageService;
    }

    @Override
    public EnvironmentType supportedType() {
        return EnvironmentType.S3;
    }

    @Override
    protected boolean checkOnPrimaryCluster(String primaryClusterIdFilePath) {
        if (!primaryClusterIdFilePath.startsWith(S3_SCHEME_PREFIX)) {
            return false;
        }

        var s3Location = toLocation(primaryClusterIdFilePath);
        try {
            getClient()
                    .headObject(HeadObjectRequest.builder()
                            .bucket(s3Location.bucket)
                            .key(s3Location.key)
                            .build());
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    protected void doCopyIfChanged(String localFile, String remoteFile) throws Exception {
        var s3Location = toLocation(remoteFile);
        var localPath = Paths.get(localFile);
        var localSize = Files.size(localPath);
        var localMtime = Files.getLastModifiedTime(localPath).toMillis();

        boolean isCopy = true;
        try {
            var head = getClient()
                    .headObject(HeadObjectRequest.builder()
                            .bucket(s3Location.bucket)
                            .key(s3Location.key)
                            .build());
            isCopy = localSize != head.contentLength()
                    || localMtime > head.lastModified().toEpochMilli();
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e;
            }
        }

        if (isCopy) {
            getClient()
                    .putObject(
                            PutObjectRequest.builder()
                                    .bucket(s3Location.getBucket())
                                    .key(s3Location.getKey())
                                    .build(),
                            RequestBody.fromFile(localPath));
        }
    }

    @Override
    public void writeToFilePath(String filePath, String content) {
        var s3Location = toLocation(filePath);
        getClient()
                .putObject(
                        PutObjectRequest.builder()
                                .bucket(s3Location.bucket)
                                .key(s3Location.key)
                                .build(),
                        RequestBody.fromString(content));
    }

    @Override
    public String buildTempPath(String... segments) {
        var storageRoot = storageService.getRootPath();
        if (!storageRoot.startsWith(S3_SCHEME_PREFIX)) {
            throw new IllegalStateException(
                    "S3 dispatch tmp path requires storage on S3; current storage root: " + storageRoot);
        }
        var base = storageRoot + SLASH + "tmp";
        return segments.length == 0 ? base : base + SLASH + String.join(SLASH, segments);
    }
}
