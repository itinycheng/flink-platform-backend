package com.flink.platform.common.model;

import lombok.Data;
import org.jspecify.annotations.NullMarked;

import static com.flink.platform.common.constants.Constant.SLASH;

/** Bucket + key pair derived from a {@code s3://bucket/key} URI. */
@Data
@NullMarked
public class S3Location {

    public final String bucket;
    public final String key;

    public S3Location(String bucket, String key) {
        if (bucket.isEmpty()) {
            throw new IllegalArgumentException("bucket is empty");
        }

        this.bucket = bucket;
        this.key = key;
    }

    public boolean isFilePath() {
        return !key.isEmpty() && !key.endsWith(SLASH);
    }
}
