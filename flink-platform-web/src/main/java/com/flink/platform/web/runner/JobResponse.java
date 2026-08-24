package com.flink.platform.web.runner;

import com.flink.platform.common.enums.ExecutionStatus;
import org.jspecify.annotations.Nullable;

import static com.flink.platform.common.enums.ExecutionStatus.ERROR;

/** Job response. */
public record JobResponse(long jobId, @Nullable Long jobRunId, ExecutionStatus status) {

    public static final JobResponse ABORTED = new JobResponse(-1L, null, ERROR);
}
