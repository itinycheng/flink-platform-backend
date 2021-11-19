package com.flink.platform.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.graph.Vertex;
import lombok.Getter;
import lombok.Setter;

/** Job vertex. */
@Getter
@Setter
public class JobVertex extends Vertex<Long> {

    private Long jobId;

    private Long jobRunId;

    private ExecutionStatus jobRunStatus;

    private ExecutionCondition precondition;

    @JsonIgnore private Object object;

    public JobVertex(Long id) {
        super(id);
    }

    public <T> T unwrapObject(Class<T> clazz) {
        return clazz.isInstance(object) ? clazz.cast(object) : null;
    }
}
