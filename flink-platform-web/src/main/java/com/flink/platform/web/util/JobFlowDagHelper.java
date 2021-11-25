package com.flink.platform.web.util;

import com.flink.platform.common.graph.DAG;
import com.flink.platform.common.model.JobEdge;
import com.flink.platform.common.model.JobVertex;

import java.util.stream.Stream;

import static com.flink.platform.common.model.ExecutionCondition.AND;
import static com.flink.platform.common.model.ExecutionCondition.OR;

/** Dag helper for job flow. */
public class JobFlowDagHelper {

    public static boolean isValid(DAG<Long, JobVertex, JobEdge> jobFlowGraph) {
        return !jobFlowGraph.getVertices().isEmpty() && !jobFlowGraph.getEdges().isEmpty();
    }

    public static boolean isPreconditionSatisfied(
            JobVertex toVertex, DAG<Long, JobVertex, JobEdge> dag) {
        Stream<JobVertex> stream = dag.getPreVertices(toVertex).stream();
        if (toVertex.getPrecondition() == AND) {
            return stream.allMatch(
                    fromVertex ->
                            fromVertex.getJobRunStatus()
                                    == dag.getEdge(fromVertex, toVertex).getExpectStatus());
        } else if (toVertex.getPrecondition() == OR) {
            return stream.anyMatch(
                    fromVertex ->
                            fromVertex.getJobRunStatus()
                                    == dag.getEdge(fromVertex, toVertex).getExpectStatus());
        } else {
            throw new IllegalStateException("Unhandled status");
        }
    }
}
