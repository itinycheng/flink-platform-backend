package com.flink.platform.storage.s3;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import com.flink.platform.storage.BackendProperties;
import com.flink.platform.storage.StorageProperties;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for {@link S3StorageSystem} backed by Adobe S3Mock. Drives the same
 * {@code endpoint} + {@code path-style-access} code path used in production against any
 * S3-compatible service (real S3, SeaweedFS, MinIO, etc.).
 *
 * <p>Requires a running Docker daemon. Testcontainers starts/stops the S3Mock container.
 */
@Testcontainers
@EnabledIf("isDockerAvailable")
class S3StorageSystemTest {

    @SuppressWarnings("unused")
    static boolean isDockerAvailable() {
        var available = DockerClientFactory.instance().isDockerAvailable();
        if (!available) {
            System.err.println("[WARN] Skipping S3StorageSystemTest: Docker daemon is not available. "
                    + "Start Docker (or set up a remote DOCKER_HOST) to run S3 integration tests.");
        }
        return available;
    }

    private static final String BUCKET = "test-bucket";

    @Container
    static final S3MockContainer S3MOCK = new S3MockContainer("5.0.0");

    private static S3StorageSystem storage;

    @BeforeAll
    static void setUp() {
        try (var admin = S3Client.builder()
                .endpointOverride(URI.create(S3MOCK.getHttpEndpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            admin.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        }

        var props = getStorageProperties();
        storage = new S3StorageSystem(props);
        storage.open();
    }

    @AfterAll
    static void tearDown() {
        if (storage != null) {
            storage.close();
        }
    }

    @Test
    void rootPathIsBucketPlusBasePath() {
        assertEquals("s3://" + BUCKET + "/platform", storage.getRootPath());
    }

    @Test
    void createFile_thenExistsAndStatus() throws Exception {
        var path = "s3://" + BUCKET + "/dir1/hello.txt";
        storage.createFile(path, "hello", true);

        assertTrue(storage.exists(path));
        var status = storage.getFileStatus(path);
        assertEquals(5L, status.getByteLength());
    }

    @Test
    void uploadAndDownload_roundTrip(@TempDir Path tmp) throws Exception {
        var localSrc = tmp.resolve("upload.txt");
        Files.writeString(localSrc, "round-trip");
        var s3Path = "s3://" + BUCKET + "/round/upload.txt";

        storage.copyFromLocalFile(localSrc.toString(), s3Path, false, true);
        assertTrue(storage.exists(s3Path));

        var localDst = tmp.resolve("download.txt");
        storage.copyToLocalFile(s3Path, localDst.toString());
        assertEquals("round-trip", Files.readString(localDst));
    }

    @Test
    void rename_movesObject() throws Exception {
        var src = "s3://" + BUCKET + "/r/a.json";
        var dst = "s3://" + BUCKET + "/r/b.json";
        storage.createFile(src, "{}", true);

        assertTrue(storage.rename(src, dst));
        assertFalse(storage.exists(src));
        assertTrue(storage.exists(dst));
    }

    @Test
    void delete_singleObject() throws Exception {
        var path = "s3://" + BUCKET + "/del/one.txt";
        storage.createFile(path, "x", true);

        assertTrue(storage.delete(path, false));
        assertFalse(storage.exists(path));
    }

    @Test
    @Disabled("S3StorageSystem does not yet implement recursive prefix deletion")
    void delete_recursivePrefix() throws Exception {
        storage.createFile("s3://" + BUCKET + "/multi/a.txt", "1", true);
        storage.createFile("s3://" + BUCKET + "/multi/b.txt", "2", true);
        storage.createFile("s3://" + BUCKET + "/multi/sub/c.txt", "3", true);

        assertTrue(storage.delete("s3://" + BUCKET + "/multi/", true));

        assertFalse(storage.exists("s3://" + BUCKET + "/multi/a.txt"));
        assertFalse(storage.exists("s3://" + BUCKET + "/multi/b.txt"));
        assertFalse(storage.exists("s3://" + BUCKET + "/multi/sub/c.txt"));
    }

    @Test
    void mkdir_isNoOp() {
        // S3 has no real directories; mkdir always succeeds without creating anything.
        assertTrue(storage.mkdir("s3://" + BUCKET + "/anywhere"));
    }

    @Test
    void s3aSchemeIsAccepted() throws Exception {
        var s3Path = "s3://" + BUCKET + "/scheme/file.txt";
        storage.createFile(s3Path, "x", true);
        // s3a:// alias must resolve to the same object.
        var s3aPath = "s3a://" + BUCKET + "/scheme/file.txt";
        assertTrue(storage.exists(s3aPath));
        var status = storage.getFileStatus(s3aPath);
        assertEquals(1L, status.getByteLength());
    }

    @Test
    void copyFromLocal_overwriteFalseRejectsExisting(@TempDir Path tmp) throws Exception {
        var localSrc = tmp.resolve("src.txt");
        Files.writeString(localSrc, "first");
        var path = "s3://" + BUCKET + "/overwrite/x.txt";

        // First write succeeds.
        storage.copyFromLocalFile(localSrc.toString(), path, false, false);
        assertTrue(storage.exists(path));

        // Second write with overwrite=false must reject.
        Files.writeString(localSrc, "second");
        var ex = assertThrows(
                IOException.class, () -> storage.copyFromLocalFile(localSrc.toString(), path, false, false));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    void existsReturnsFalseForDirectoryLikePath() throws Exception {
        // Align with HDFS semantics: exists returns true/false, never throws for absent keys.
        // S3 has no real dirs, so a "dir/" key with no marker object simply does not exist.
        assertFalse(storage.exists("s3://" + BUCKET + "/dir/"));
    }

    @Test
    void deleteRejectsDirectoryLikePath() {
        assertThrows(IllegalStateException.class, () -> storage.delete("s3://" + BUCKET + "/dir/", false));
    }

    @Test
    void mutatingOpsRejectDirectoryLikePath(@TempDir Path tmpDir) throws Exception {
        var dirLike = "s3://" + BUCKET + "/dir/";
        var local = tmpDir.resolve("local.txt");
        Files.writeString(local, "x");

        assertThrows(IllegalStateException.class, () -> storage.createFile(dirLike, "x", true));
        assertThrows(
                IllegalStateException.class, () -> storage.copyFromLocalFile(local.toString(), dirLike, false, true));
        assertThrows(IllegalStateException.class, () -> storage.getFileStatus(dirLike));
        assertThrows(IllegalStateException.class, () -> storage.copyToLocalFile(dirLike, local.toString()));
        assertThrows(IllegalStateException.class, () -> storage.rename(dirLike, "s3://" + BUCKET + "/dir2/file.txt"));
        assertThrows(IllegalStateException.class, () -> storage.rename("s3://" + BUCKET + "/dir2/file.txt", dirLike));
    }

    @Test
    void getParentPath_handlesAllForms() {
        assertEquals("s3://" + BUCKET + "/a/b", storage.getParentPath("s3://" + BUCKET + "/a/b/c.txt"));
        assertEquals("s3://" + BUCKET + "/a", storage.getParentPath("s3://" + BUCKET + "/a/b/"));
        assertEquals("s3://" + BUCKET, storage.getParentPath("s3://" + BUCKET + "/a"));
        // Bucket-root has no parent — return as-is rather than chopping into the scheme.
        assertEquals("s3://" + BUCKET, storage.getParentPath("s3://" + BUCKET));
        assertEquals("s3://" + BUCKET, storage.getParentPath("s3://" + BUCKET + "/"));
    }

    @Test
    void getParentPath_rejectsNonS3Uri() {
        assertThrows(IllegalArgumentException.class, () -> storage.getParentPath("a/b"));
        assertThrows(IllegalArgumentException.class, () -> storage.getParentPath("/foo/bar"));
        assertThrows(IllegalArgumentException.class, () -> storage.getParentPath("hdfs://nn/foo"));
    }

    @Test
    void parseRejectsEmptyBucket() {
        // S3Location's constructor enforces non-empty bucket; "s3:///key" parses to empty bucket.
        var ex = assertThrows(IllegalArgumentException.class, () -> storage.exists("s3:///key"));
        assertTrue(ex.getMessage().contains("bucket is empty"));
    }

    @Test
    void rename_dstExists_returnsFalseAndKeepsSrc() throws Exception {
        var src = "s3://" + BUCKET + "/rename/src.txt";
        var dst = "s3://" + BUCKET + "/rename/dst.txt";
        storage.createFile(src, "src-content", true);
        storage.createFile(dst, "dst-content", true);

        // dst already exists → match HDFS rename semantics: return false, leave src in place.
        assertFalse(storage.rename(src, dst));
        assertTrue(storage.exists(src));
        assertTrue(storage.exists(dst));
    }

    @Test
    void rename_samePath_isNoOp() throws Exception {
        var path = "s3://" + BUCKET + "/rename/same.txt";
        storage.createFile(path, "x", true);
        // Same source and destination — must not delete the file.
        assertTrue(storage.rename(path, path));
        assertTrue(storage.exists(path));
    }

    @Test
    void open_failsWhenEndpointSetButPathStyleDisabled() {
        // Custom endpoint without path-style addressing breaks MinIO/SeaweedFS — must fail fast.
        var props = new StorageProperties();
        props.setType("s3");
        props.setBasePath("platform");
        var backends = new BackendProperties();
        backends.setS3(new BackendProperties.S3Properties(
                BUCKET, "us-east-1", S3MOCK.getHttpEndpoint(), false, "test", "test"));
        props.setBackends(backends);

        try (var storage = new S3StorageSystem(props)) {
            var ex = assertThrows(IllegalStateException.class, storage::open);
            assertTrue(ex.getMessage().contains("path-style-access"));
        }
    }

    // ==================================================================
    // ========================= private ================================
    // ==================================================================
    private static @NonNull StorageProperties getStorageProperties() {
        var props = new StorageProperties();
        props.setType("s3");
        props.setBasePath("platform");
        var backends = new BackendProperties();
        backends.setS3(new BackendProperties.S3Properties(
                BUCKET, "us-east-1", S3MOCK.getHttpEndpoint(), true, "test", "test"));
        props.setBackends(backends);
        return props;
    }
}
