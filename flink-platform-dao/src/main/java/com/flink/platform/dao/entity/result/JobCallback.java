package com.flink.platform.dao.entity.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.flink.platform.common.enums.ExecutionStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

import javax.annotation.Nonnull;

/** call back info from the command line. */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobCallback {

    /** flink job id. */
    private String jobId;

    /** hadoop application id. */
    private String appId;

    private String trackingUrl;

    private String message;

    @JsonIgnore private ExecutionStatus status;

    @Nonnull @Delegate @JsonIgnore private ShellCallback cmdCallback;

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
