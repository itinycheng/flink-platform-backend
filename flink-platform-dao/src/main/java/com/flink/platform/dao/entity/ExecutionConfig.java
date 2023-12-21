package com.flink.platform.dao.entity;

import com.flink.platform.common.enums.ExecutionStrategy;
import lombok.Data;

/**
 * JobFlow execution config.
 */
@Data
public class ExecutionConfig {
    private ExecutionStrategy strategy;

    private Long startJobId;

    private boolean visibleToDependent;
}
