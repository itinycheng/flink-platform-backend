package com.flink.platform.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.graph.Vertex;
import com.flink.platform.common.util.Preconditions;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static com.flink.platform.common.model.ExecutionCondition.AND;

/** Job vertex. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class JobVertex extends Vertex<Long> {

    private Long jobId;

    private ExecutionCondition precondition = AND;

    @JsonIgnore private transient LocalDateTime submitTime;

    @JsonIgnore private transient Long jobRunId;

    @JsonIgnore private transient ExecutionStatus jobRunStatus;

    public JobVertex(Long id, Long jobId) {
        super(id);
        this.jobId = Preconditions.checkNotNull(jobId);
    }
}
