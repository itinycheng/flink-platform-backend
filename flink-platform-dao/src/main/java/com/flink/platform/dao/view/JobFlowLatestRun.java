package com.flink.platform.dao.view;

import com.flink.platform.common.enums.ExecutionStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A job flow's schedule together with its most recent run.
 */
@Data
public class JobFlowLatestRun {

    private Long flowId;

    private String flowCronExpr;

    private Long latestRunId;

    private ExecutionStatus latestStatus;

    private LocalDateTime latestCreateTime;
}
