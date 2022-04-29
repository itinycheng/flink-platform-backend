package com.flink.platform.common.enums;

/** response status. */
public enum ResponseStatus {
    /** response status. */
    SUCCESS(0, "success"),
    EXCESSIVE_FREQUENCY(201, "Excessive frequency"),
    ERROR_PARAMETER(301, "illegal input parameter"),
    UNAUTHORIZED(401, "Unauthorized"),
    SERVICE_ERROR(500, "service error"),
    SERVICE_TIMEOUT(501, "service timeout"),
    USER_NOT_FOUNT(502, "user not found"),
    USER_HAVE_NO_PERMISSION(502, "user have no permission"),
    USER_NAME_PASSWD_ERROR(10013, "Username or password error"),
    NOT_RUNNABLE_STATUS(10014, "not runnable status"),
    NO_CRONTAB_SET(10015, "no crontab set"),
    UNABLE_SCHEDULE_STREAMING_JOB(10016, "unable to schedule streaming job"),
    FILE_NOT_FOUND(10017, "file not found"),
    EXIST_UNFINISHED_PROCESS(10018, "There is an unfinished job or flow"),
    DATASOURCE_NOT_FOUND(10019, "Datasource not found");

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
