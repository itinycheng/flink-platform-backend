package com.itiger.persona.common.enums;


/**
 * @author tiny.wang
 */
public enum ResponseStatus {
    /**
     * response status
     */
    SUCCESS(200, "success"),
    EXCESSIVE_FREQUENCY(201, "Excessive frequency"),
    ERROR_PARAMETER(301, "illegal input parameter"),
    UNAUTHORIZED(401, "Unauthorized"),
    SERVICE_ERROR(500, "service error"),
    SERVICE_TIMEOUT(501, "service timeout"),
    USER_NOT_FOUNT(502, "user not found");

    private final int code;
    private final String desc;

    ResponseStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
