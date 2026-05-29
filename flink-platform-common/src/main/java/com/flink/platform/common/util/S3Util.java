package com.flink.platform.common.util;

import com.flink.platform.common.model.S3Location;

import static com.flink.platform.common.constants.Constant.SLASH;

/**
 * s3 utils.
 */
public class S3Util {

    public static final String S3_SCHEME_PREFIX = "s3://";

    public static final String S3A_SCHEME_PREFIX = "s3a://";

    public static S3Location toLocation(String pathStr) {
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

        int idx = stripped.indexOf(SLASH);
        String bucket = idx < 0 ? stripped : stripped.substring(0, idx);
        String key = idx < 0 ? "" : stripped.substring(idx + 1);
        return new S3Location(bucket, key);
    }
}
