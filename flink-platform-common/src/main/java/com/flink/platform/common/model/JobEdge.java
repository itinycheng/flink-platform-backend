package com.flink.platform.common.model;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.graph.Edge;
import lombok.Getter;
import lombok.Setter;

/** json edge. */
@Getter
@Setter
public class JobEdge extends Edge<JobVertex> {

    private ExecutionStatus expectStatus;

    private ExecutionStatus actualStatus;

    public JobEdge(
            JobVertex fromVertex,
            JobVertex toVertex,
            ExecutionStatus expectStatus,
            ExecutionStatus actualStatus) {
        super(fromVertex, toVertex);
        this.expectStatus = expectStatus;
        this.actualStatus = actualStatus;
    }
}
