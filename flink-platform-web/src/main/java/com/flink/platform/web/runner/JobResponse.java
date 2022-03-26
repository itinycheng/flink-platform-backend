package com.flink.platform.web.runner;

import com.flink.platform.common.enums.ExecutionStatus;
import lombok.Data;

/** Job response. */
@Data
public class JobResponse {

    private final long jobId;

    private final Long jobRunId;

    private final ExecutionStatus status;
}
