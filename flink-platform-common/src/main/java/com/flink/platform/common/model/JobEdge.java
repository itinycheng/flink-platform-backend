package com.flink.platform.common.model;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.graph.Edge;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import static com.flink.platform.common.util.Preconditions.checkNotNull;

/** Json edge. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class JobEdge extends Edge<Long> {

    private ExecutionStatus expectStatus;

    public JobEdge(Long fromVertex, Long toVertex, ExecutionStatus expectStatus) {
        super(fromVertex, toVertex);
        this.expectStatus = checkNotNull(expectStatus);
    }
}
