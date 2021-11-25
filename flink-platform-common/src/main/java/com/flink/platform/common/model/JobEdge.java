package com.flink.platform.common.model;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.graph.Edge;
import lombok.Getter;
import lombok.Setter;

/** json edge. */
@Getter
@Setter
public class JobEdge extends Edge<Long> {

    private ExecutionStatus expectStatus;

    public JobEdge(Long fromVertex, Long toVertex, ExecutionStatus expectStatus) {
        super(fromVertex, toVertex);
        this.expectStatus = expectStatus;
    }
}
