package com.flink.platform.web.util;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.graph.DAG;
import com.flink.platform.common.model.ExecutionCondition;
import com.flink.platform.common.model.JobEdge;
import com.flink.platform.common.model.JobVertex;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static com.flink.platform.common.enums.ExecutionStatus.ABNORMAL;
import static com.flink.platform.common.enums.ExecutionStatus.ERROR;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.KILLED;
import static com.flink.platform.common.enums.ExecutionStatus.NOT_EXIST;
import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.common.model.ExecutionCondition.AND;
import static com.flink.platform.common.model.ExecutionCondition.OR;
import static java.util.stream.Collectors.toSet;

/** Dag helper for job flow. */
public class JobFlowDagHelper {

    // TODO : dag can not have executable vertices
    public static ExecutionStatus getDagState(DAG<Long, JobVertex, JobEdge> dag) {
        Set<ExecutionStatus> vertexStatusList =
                dag.getVertices().stream()
                        .map(JobVertex::getJobRunStatus)
                        .filter(Objects::nonNull)
                        .collect(toSet());
        ExecutionStatus status;
        if (vertexStatusList.contains(ERROR)) {
            status = ERROR;
        } else if (vertexStatusList.contains(NOT_EXIST)) {
            status = NOT_EXIST;
        } else if (vertexStatusList.contains(FAILURE)) {
            status = FAILURE;
        } else if (vertexStatusList.contains(ABNORMAL)) {
            status = ABNORMAL;
        } else if (vertexStatusList.contains(KILLED)) {
            status = KILLED;
        } else if (vertexStatusList.contains(SUCCESS)) {
            status = SUCCESS;
        } else if (vertexStatusList.contains(RUNNING)) {
            status = RUNNING;
        } else {
            status = SUBMITTED;
        }

        return status;
    }

    public static boolean isPreconditionSatisfied(
            JobVertex toVertex, DAG<Long, JobVertex, JobEdge> dag) {
        Collection<JobVertex> preVertices = dag.getPreVertices(toVertex);
        if (CollectionUtils.isEmpty(preVertices)) {
            return true;
        }

        ExecutionCondition precondition = toVertex.getPrecondition();
        if (precondition == AND) {
            return preVertices.stream()
                    .allMatch(
                            fromVertex ->
                                    fromVertex.getJobRunStatus()
                                            == dag.getEdge(fromVertex, toVertex).getExpectStatus());
        } else if (precondition == OR) {
            return preVertices.stream()
                    .anyMatch(
                            fromVertex ->
                                    fromVertex.getJobRunStatus()
                                            == dag.getEdge(fromVertex, toVertex).getExpectStatus());
        } else {
            throw new IllegalStateException("Can't handle precondition status: " + precondition);
        }
    }

    public static Set<JobVertex> getExecutableVertices(DAG<Long, JobVertex, JobEdge> dag) {
        // Return the beginning vertices, if there are any not executed.
        Collection<JobVertex> beginVertices = dag.getBeginVertices();
        Set<JobVertex> executableSet =
                beginVertices.stream()
                        .filter(jobVertex -> jobVertex.getJobRunStatus() == null)
                        .collect(toSet());

        // TODO Handle the situation where any vertex has a failed state.
        if (CollectionUtils.isNotEmpty(executableSet)) {
            return executableSet;
        }

        return getNextExecutableVertices(beginVertices, dag);
    }

    private static Set<JobVertex> getNextExecutableVertices(
            Collection<JobVertex> fromVertices, DAG<Long, JobVertex, JobEdge> dag) {

        // Get the edges whose status matched his formVertex's status.
        Set<JobEdge> statusMatchedEdgeSet =
                fromVertices.stream()
                        .flatMap(
                                fromVertex ->
                                        dag.getEdgesFromVertex(fromVertex).stream()
                                                .map(edge -> edge.unwrap(JobEdge.class))
                                                .filter(
                                                        edge ->
                                                                edge.getExpectStatus()
                                                                        == fromVertex
                                                                                .getJobRunStatus()))
                        .collect(toSet());

        // Only execute edge with failed status, if there are any.
        Set<JobEdge> failedEdges =
                statusMatchedEdgeSet.stream()
                        .filter(edge -> edge.getExpectStatus().isErrTerminalState())
                        .collect(toSet());
        if (CollectionUtils.isNotEmpty(failedEdges)) {
            statusMatchedEdgeSet = failedEdges;
        }

        // Get the executable vertices.
        Set<JobVertex> executableToVertices =
                statusMatchedEdgeSet.stream()
                        .map(edge -> dag.getVertex(edge.getToVId()))
                        .filter(toVertex -> isPreconditionSatisfied(toVertex, dag))
                        .collect(toSet());

        // If toVertex is executed, use it as fromVertex to find the next executable vertex.
        Set<JobVertex> executedVertices = new HashSet<>();
        Set<JobVertex> unExecutedVertices = new HashSet<>();
        for (JobVertex executableToVertex : executableToVertices) {
            if (executableToVertex.getJobRunStatus() != null) {
                executedVertices.add(executableToVertex);
            } else {
                unExecutedVertices.add(executableToVertex);
            }
        }

        if (CollectionUtils.isNotEmpty(executedVertices)) {
            unExecutedVertices.addAll(getNextExecutableVertices(executedVertices, dag));
        }

        return unExecutedVertices;
    }
}
