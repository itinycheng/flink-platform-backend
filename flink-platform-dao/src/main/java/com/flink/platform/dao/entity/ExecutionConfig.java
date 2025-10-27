package com.flink.platform.dao.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flink.platform.common.enums.ExecutionStrategy;
import lombok.Data;

/**
 * JobFlow execution config.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecutionConfig {

    private ExecutionStrategy strategy;

    private Long startJobId;

    private boolean visibleToDependent;

    /**
     * Both used in JobFlow and JobFlowRun.
     */
    private int parallelism;
}
