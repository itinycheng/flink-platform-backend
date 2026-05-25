package com.flink.platform.storage.s3;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import com.flink.platform.storage.BackendProperties;
import com.flink.platform.storage.StorageProperties;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for {@link S3StorageSystem} backed by Adobe S3Mock. Drives the same
 * {@code endpoint} + {@code path-style-access} code path used in production against any
 * S3-compatible service (real S3, SeaweedFS, MinIO, etc.).
 *
 * <p>Requires a running Docker daemon. Testcontainers starts/stops the S3Mock container.
 */
@Testcontainers
class S3StorageSystemTest {

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
