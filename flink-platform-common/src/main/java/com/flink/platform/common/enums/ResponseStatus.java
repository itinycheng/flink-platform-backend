package com.flink.platform.common.enums;

import lombok.Getter;

/** response status. */
@Getter
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
    INVALID_CRONTAB_EXPR(10015, "invalid quartz crontab expression"),
    UNABLE_SCHEDULING_JOB_FLOW(10016, "unable to schedule job flow for now"),
    FILE_NOT_FOUND(10017, "file not found"),
    EXIST_UNFINISHED_PROCESS(10018, "There is an unfinished job or flow"),
    DATASOURCE_NOT_FOUND(10019, "Datasource not found"),
    FLOW_ALREADY_TERMINATED(10020, "Workflow already terminated"),
    JOB_ALREADY_TERMINATED(10021, "Job already terminated"),
    KILL_FLOW_EXCEPTION_FOUND(10022, "Exception raised when terminating workflow"),
    NO_RUNNING_JOB_FOUND(10023, "No running job found"),
    OPERATION_NOT_ALLOWED(10024, "illegal operation"),
    FILE_EXISTS(10025, "file already exists"),
    JOB_LIST_NOT_SUPPORT_SCHEDULING(10026, "Job list does not support scheduling"),
    FLOW_ALREADY_SCHEDULED(10027, "Workflow already being scheduled"),
    INVALID_WORKFLOW_TYPE(10028, "invalid workflow type"),
    INVALID_STATUS(10029, "invalid status"),
    ;

    private final int code;
    private final String desc;

    ResponseStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
