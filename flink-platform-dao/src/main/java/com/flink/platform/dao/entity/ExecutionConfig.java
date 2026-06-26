package com.flink.platform.dao.entity;

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

    /**
     * User-supplied override for the biz* anchor.
     * Null = no override; effective anchor falls back to t_job_flow_run.schedule_time.
     */
    private LocalDateTime scheduleTime;

    /**
     * Both used in JobFlow and JobFlowRun.
     */
    private int parallelism;
}
