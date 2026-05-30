package com.flink.platform.sql.submit.base;

import com.flink.platform.common.model.S3Location;
import com.flink.platform.common.util.IOUtil;
import com.flink.platform.common.util.S3Util;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.io.IOException;
import java.io.InputStream;

import static com.flink.platform.common.constants.Constant.SLASH;

/**
 * s3 compatible path implementation.
 */
public class S3Path implements CommonPath {

    private final String bucket;

    private final String key;

    public S3Path(String filePath) {
        S3Location location = S3Util.toLocation(filePath);
        if (location.key.isEmpty()) {
            throw new IllegalArgumentException("invalid S3 path (missing key): " + filePath);
        }

        this.bucket = location.bucket;
        this.key = location.key;
    }

    @Override
    public String getName() {
        int idx = key.lastIndexOf(SLASH);
        return idx < 0 ? key : key.substring(idx + 1);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        S3Client s3 = newClient();
        try {
            InputStream stream = s3.getObject(b -> b.bucket(bucket).key(key));
            return IOUtil.closingWith(stream, s3);
        } catch (Exception e) {
            try {
                s3.close();
            } catch (Exception ignore) {
            }
            throw new IOException("Failed to get S3 object: s3://" + bucket + SLASH + key, e);
        }
    }

    private S3Client newClient() {
        S3ClientBuilder builder = S3Client.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .forcePathStyle(S3Util.shouldForcePathStyle());
        return builder.build();
    }
}
