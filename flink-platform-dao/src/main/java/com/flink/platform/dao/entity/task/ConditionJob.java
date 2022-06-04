package com.flink.platform.dao.entity.task;

import com.flink.platform.common.enums.ExecutionCondition;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** condition job. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ConditionJob extends BaseJob {

    private ExecutionCondition condition;
}
