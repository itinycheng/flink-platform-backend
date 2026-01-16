package com.flink.platform.dao.entity.task;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.InheritParamMode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FlowJob extends BaseJob {

    private long flowId;

    private InheritParamMode inheritParamMode;

    private Set<String> paramNames;

    private ExecutionStatus expectedFailureCorrectedTo;
}
