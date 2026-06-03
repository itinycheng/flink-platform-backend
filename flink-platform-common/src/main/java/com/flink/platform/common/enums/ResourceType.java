package com.flink.platform.common.enums;

/** Resource type. */
public enum ResourceType {
    FILE,
    DIR;

    public boolean isFile() {
        return this == FILE;
    }

    public boolean isDir() {
        return this == DIR;
    }
}
