package com.flink.platform.web.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.web.util.ShellCallback;
import lombok.Data;
import lombok.experimental.Delegate;

/** call back info from the command line. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobCallback {

    /** flink job id. */
    private String jobId;

    /** hadoop application id. */
    private String appId;

    private String trackingUrl;

    private String message;

    @JsonIgnore private ExecutionStatus status;

    @Delegate @JsonIgnore private ShellCallback cmdCallback;

    /** Only for deSeral. */
    public JobCallback() {
        this(null, null);
    }

    public JobCallback(String message, ExecutionStatus status) {
        this(null, message, status);
    }

    public JobCallback(ShellCallback cmdCallback, String message, ExecutionStatus status) {
        this(null, null, null, cmdCallback, message, status);
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
        this.cmdCallback = cmdCallback != null ? cmdCallback : new ShellCallback();
        this.message = message;
        this.status = status;
    }
}
