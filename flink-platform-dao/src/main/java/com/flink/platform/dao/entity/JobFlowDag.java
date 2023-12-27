package com.flink.platform.dao.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flink.platform.common.graph.DAG;
import com.flink.platform.common.model.JobEdge;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.util.JobFlowDagHelper;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Job flow dag. */
@Getter
public class JobFlowDag extends DAG<Long, JobVertex, JobEdge> {

    private final Map<Long, NodeLayout> nodeLayouts;

    private final Map<Long, EdgeLayout> edgeLayouts;

    @Setter
    @JsonIgnore
    private transient ExecutionConfig config;

    public JobFlowDag() {
        this.nodeLayouts = new HashMap<>();
        this.edgeLayouts = new HashMap<>();
    }

    @Override
    public Collection<JobVertex> getBeginVertices() {
        if (config == null || config.getStrategy() == null) {
            return super.getBeginVertices();
        }

        switch (config.getStrategy()) {
            case ONLY_CUR_JOB:
                JobVertex vertex = super.getVertex(config.getStartJobId());
                if (vertex == null) {
                    throw new IllegalArgumentException("Start vertex not found: " + config.getStartJobId());
                }
                return Collections.singleton(vertex);
            case ALL_POST_JOBS:
            case ALL_PRE_JOBS:
            default:
                throw new IllegalArgumentException("Unsupported execution strategy: " + config.getStrategy());
        }
    }

    @Override
    public Collection<JobVertex> getNextVertices(JobVertex vertex) {
        if (config == null || config.getStrategy() == null) {
            return super.getNextVertices(vertex);
        }

        switch (config.getStrategy()) {
            case ONLY_CUR_JOB:
                return Collections.emptyList();
            case ALL_POST_JOBS:
            case ALL_PRE_JOBS:
            default:
                throw new IllegalArgumentException("Unsupported execution strategy: " + config.getStrategy());
        }
    }

    public boolean hasUnExecutedVertices() {
        if (config == null || config.getStrategy() == null) {
            return JobFlowDagHelper.hasUnExecutedVertices(this);
        }

        switch (config.getStrategy()) {
            case ONLY_CUR_JOB:
                JobVertex vertex = super.getVertex(config.getStartJobId());
                if (vertex == null) {
                    return false;
                }

                return vertex.getJobRunId() == null && vertex.getJobRunStatus() == null;
            case ALL_POST_JOBS:
            case ALL_PRE_JOBS:
            default:
                throw new IllegalArgumentException("Unsupported execution strategy: " + config.getStrategy());
        }
    }

    public boolean isPreconditionSatisfied(JobVertex toVertex) {
        if (config == null || config.getStrategy() == null) {
            return JobFlowDagHelper.isPreconditionSatisfied(toVertex, this);
        }

        switch (config.getStrategy()) {
            case ONLY_CUR_JOB:
                return false;
            case ALL_POST_JOBS:
            case ALL_PRE_JOBS:
            default:
                throw new IllegalArgumentException("Unsupported execution strategy: " + config.getStrategy());
        }
    }

    @JsonIgnore
    @Override
    public boolean isValid() {
        boolean isValidVertices = this.getVertices().stream()
                .allMatch(vertex ->
                        vertex.getId() != null && vertex.getJobId() != null && vertex.getPrecondition() != null);
        boolean isValidEdges = this.getEdges().stream()
                .allMatch(
                        edge -> edge.getFromVId() != null && edge.getToVId() != null && edge.getExpectStatus() != null);

        return isValidVertices && isValidEdges && super.isValid();
    }

    @Data
    @NoArgsConstructor
    static class NodeLayout {
        private String id;
        private String type;
        private int x;
        private int y;
    }

    @Data
    @NoArgsConstructor
    static class EdgeLayout {
        private String id;
    }
}
