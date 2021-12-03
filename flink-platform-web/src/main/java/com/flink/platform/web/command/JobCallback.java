package com.flink.platform.web.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flink.platform.common.enums.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** call back info from the command line. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobCallback {

    /** flink job id. */
    private String jobId;

    /** hadoop application id. */
    private String appId;

    /** callback log. */
    private String message;

    @JsonIgnore private ExecutionStatus status;
}
