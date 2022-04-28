package com.flink.platform.dao.entity.alert;

import com.flink.platform.common.enums.ExecutionStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Alert config for JobFlow and JobFlowRun. */
@Data
@NoArgsConstructor
public class AlertConfig {

    /** alert ids. */
    Long alertId;

    /** trigger statuses. */
    List<ExecutionStatus> statuses;
}
