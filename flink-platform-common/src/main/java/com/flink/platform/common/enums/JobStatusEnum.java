package com.flink.platform.common.enums;

/**
 * @Author Shik
 * @Title: SignatureTypeEnum
 * @ProjectName: datapipeline
 * @Description: TODO
 * @Date: 2021/3/30 下午4:43
 */
public enum JobStatusEnum {

    NEW(1, "new"),
    READY(2, "ready"),
    SCHEDULED(3, "scheduled"),
    STOPPED(4, "stopped"),
    FAILED(5, "failed"),
    DELETE(-1, "delete"),
    ;

    private int code;
    private String desc;

    JobStatusEnum(int code, String desc) {
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
