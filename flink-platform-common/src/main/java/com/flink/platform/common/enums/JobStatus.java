package com.flink.platform.common.enums;

import lombok.Getter;

/** job status. */
@Getter
public enum JobStatus {
    ONLINE(1, "online"),
    OFFLINE(0, "offline"),
    DELETE(-1, "delete");

    private final int code;
    private final String desc;

    JobStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
