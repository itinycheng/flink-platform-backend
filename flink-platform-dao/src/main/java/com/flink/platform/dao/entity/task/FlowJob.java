package com.flink.platform.dao.entity.task;

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

    private InheritParamMode inheritParamMode = InheritParamMode.ALLOW;

    private Set<String> paramNames = Set.of();
}
