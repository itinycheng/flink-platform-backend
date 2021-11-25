package com.flink.platform.common.model;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.graph.Vertex;
import com.flink.platform.common.util.Preconditions;
import lombok.Getter;
import lombok.Setter;

/** Job vertex. */
@Getter
@Setter
public class JobVertex extends Vertex<Long> {

    private final Long jobId;

    private Long jobRunId;

    private ExecutionStatus jobRunStatus;

    private ExecutionCondition precondition;

    public JobVertex(Long id, Long jobId) {
        super(id);
        this.jobId = Preconditions.checkNotNull(jobId);
    }
}
