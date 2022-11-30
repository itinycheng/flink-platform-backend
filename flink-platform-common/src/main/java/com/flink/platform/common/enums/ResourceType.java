package com.flink.platform.common.enums;

/** Alert type. */
public enum ResourceType {
    JAR,

    SHELL,

    DIR;

    public boolean isFile() {
        return !isDir();
    }

    public boolean isDir() {
        return DIR == this;
    }
}
