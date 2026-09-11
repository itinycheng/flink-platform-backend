package com.flink.platform.dao.view;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobFlowType;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import static com.flink.platform.common.enums.JobFlowType.JOB_FLOW;

/** job with related columns from joined tables. */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobDetails extends JobInfo {

    private Long jobFlowId;

    private JobFlowType jobFlowType;

    private JobFlowDag jobFlowDag;

    private Long jobRunId;

    private Long flowRunId;

    private ExecutionStatus jobRunStatus;

    public boolean isStillInUse() {
        if (JOB_FLOW != jobFlowType) {
            return true;
        }

        return jobFlowDag != null && jobFlowDag.containsVertex(getId());
    }
}
