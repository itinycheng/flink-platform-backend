package com.flink.platform.common.enums;

/** job status. */
public enum JobStatus {
    ONLINE(1, "online"),
    OFFLINE(-1, "offline");

    private int code;
    private String desc;

    JobStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
