package com.flink.platform.common.model;

import lombok.Data;

/** Bucket + key pair derived from a {@code s3://bucket/key} URI. */
@Data
public class S3Location {

    public final String bucket;
    public final String key;

    public S3Location(String bucket, String key) {
        if (bucket == null || bucket.isEmpty()) {
            throw new IllegalArgumentException("bucket is empty");
        }

        this.bucket = bucket;
        this.key = key;
    }
}
