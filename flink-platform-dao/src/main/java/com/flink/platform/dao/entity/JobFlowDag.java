package com.flink.platform.dao.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flink.platform.common.graph.DAG;
import com.flink.platform.common.model.JobEdge;
import com.flink.platform.common.model.JobVertex;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/** Job flow dag. */
public class JobFlowDag extends DAG<Long, JobVertex, JobEdge> {

    @Getter
    private final Map<Long, NodeLayout> nodeLayouts;

    @Getter
    private final Map<Long, EdgeLayout> edgeLayouts;

    public JobFlowDag() {
        this.nodeLayouts = new HashMap<>();
        this.edgeLayouts = new HashMap<>();
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
