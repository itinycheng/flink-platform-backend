package com.flink.platform.web.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.web.util.ShellCallback;
import lombok.Data;

/** call back info from the command line. */
@Data
public class JobCallback {

    /** flink job id. */
    private String jobId;

    /** hadoop application id. */
    private String appId;

    private String trackingUrl;

    private ShellCallback cmdCallback;

    /** callback log. */
    private String message;

    @JsonIgnore private ExecutionStatus status;

    public JobCallback(String message, ExecutionStatus status) {
        this.message = message;
        this.status = status;
    }

    public JobCallback(ShellCallback cmdCallback, String message, ExecutionStatus status) {
        this.cmdCallback = cmdCallback;
        this.message = message;
        this.status = status;
    }

    public JobCallback(
            String jobId,
            String appId,
            String trackingUrl,
            ShellCallback cmdCallback,
            String message,
            ExecutionStatus status) {
        this.jobId = jobId;
        this.appId = appId;
        this.trackingUrl = trackingUrl;
        this.cmdCallback = cmdCallback;
        this.message = message;
        this.status = status;
    }
}
