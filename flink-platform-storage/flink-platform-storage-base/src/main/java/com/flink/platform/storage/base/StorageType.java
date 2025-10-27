package com.flink.platform.storage.base;

import lombok.Getter;

/**
 * Storage type.
 */
@Getter
public enum StorageType {
    LOCAL(0, "local"),
    HDFS(1, "hdfs");

    private final int code;

    private final String name;

    StorageType(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static StorageType from(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        name = name.toLowerCase();
        for (StorageType storageType : StorageType.values()) {
            if (storageType.getName().equals(name)) {
                return storageType;
            }
        }

        return null;
    }
}
