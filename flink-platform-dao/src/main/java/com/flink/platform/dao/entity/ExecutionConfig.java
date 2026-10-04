package com.flink.platform.dao.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.flink.platform.common.enums.ExecutionStrategy;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * JobFlow execution config.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecutionConfig {

    private ExecutionStrategy strategy;

    private Long startJobId;

    /** Effective logical schedule time; an input value overrides the trigger time. */
    private LocalDateTime scheduleTime;

    /**
     * Maximum active flow runs for different logical schedule times.
     */
    private int maxActiveRuns;

    /**
     * Maximum concurrently executing vertices inside one flow run.
     */
    @JsonAlias("parallelism")
    private int maxJobsPerRun;

    public int defaultOrGetMaxActiveRuns() {
        if (maxActiveRuns <= 0) {
            return 1;
        }
        return maxActiveRuns;
    }

    public void mergeFrom(ExecutionConfig other) {
        if (other == null) {
            return;
        }
        if (other.strategy != null) {
            strategy = other.strategy;
        }
        if (other.startJobId != null) {
            startJobId = other.startJobId;
        }
        if (other.scheduleTime != null) {
            scheduleTime = other.scheduleTime;
        }
        if (other.maxActiveRuns > 0) {
            maxActiveRuns = other.maxActiveRuns;
        }
        if (other.maxJobsPerRun > 0) {
            maxJobsPerRun = other.maxJobsPerRun;
        }
    }
}
