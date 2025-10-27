package com.flink.platform.dao.util;

import com.flink.platform.common.enums.ExecutionCondition;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.graph.DAG;
import com.flink.platform.common.model.JobEdge;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.JobFlowDag;
import jakarta.annotation.Nonnull;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static com.flink.platform.common.enums.ExecutionCondition.AND;
import static com.flink.platform.common.enums.ExecutionCondition.OR;
import static com.flink.platform.common.enums.ExecutionStatus.ABNORMAL;
import static com.flink.platform.common.enums.ExecutionStatus.ERROR;
import static com.flink.platform.common.enums.ExecutionStatus.EXPECTED_FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.KILLED;
import static com.flink.platform.common.enums.ExecutionStatus.NOT_EXIST;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static java.util.stream.Collectors.toSet;

/** Dag helper for job flow. */
public class JobFlowDagHelper {

    @Nonnull
    public static ExecutionStatus getFinalStatus(DAG<Long, JobVertex, JobEdge> dag) {
        Set<ExecutionStatus> statusList = dag.getVertices().stream()
                .map(JobVertex::getJobRunStatus)
                .filter(Objects::nonNull)
                .collect(toSet());
        return statusList.stream()
                .filter(executionStatus -> !executionStatus.isTerminalState())
                .findFirst()
                .orElseGet(() -> {
                    // terminal status, keep the current `if.else` order is very important.
                    if (statusList.contains(ERROR)) {
                        return ERROR;
                    } else if (statusList.contains(NOT_EXIST)) {
                        return NOT_EXIST;
                    } else if (statusList.contains(ABNORMAL)) {
                        return ABNORMAL;
                    } else if (statusList.contains(KILLED)) {
                        return KILLED;
                    } else if (statusList.contains(FAILURE)) {
                        return determineFailureStatus(dag);
                    } else if (statusList.contains(SUCCESS)) {
                        return SUCCESS;
                    } else {
                        // unreachable.
                        return SUBMITTED;
                    }
                });
    }

    public static boolean hasUnExecutedVertices(JobFlowDag dag) {
        Set<JobVertex> vertices = dag.getVertices();
        if (vertices.stream().map(JobVertex::getJobRunStatus).anyMatch(ExecutionStatus::isStopFlowState)) {
            return false;
        }

        if (vertices.stream()
                .map(JobVertex::getJobRunStatus)
                .filter(Objects::nonNull)
                .anyMatch(executionStatus -> !executionStatus.isTerminalState())) {
            return true;
        }

        Collection<JobVertex> beginVertices = dag.getBeginVertices();
        if (beginVertices.stream().anyMatch(jobVertex -> jobVertex.getJobRunStatus() == null)) {
            return true;
        }

        return CollectionUtils.isNotEmpty(getNextExecutableVertices(beginVertices, dag));
    }

    public static boolean isPreconditionSatisfied(JobVertex toVertex, DAG<Long, JobVertex, JobEdge> dag) {
        Collection<JobVertex> preVertices = dag.getPreVertices(toVertex);
        if (CollectionUtils.isEmpty(preVertices)) {
            return true;
        }

        ExecutionCondition precondition = toVertex.getPrecondition();
        if (precondition == AND) {
            return preVertices.stream()
                    .allMatch(fromVertex -> fromVertex.getJobRunStatus()
                            == dag.getEdge(fromVertex, toVertex).getExpectStatus());
        } else if (precondition == OR) {
            return preVertices.stream()
                    .anyMatch(fromVertex -> fromVertex.getJobRunStatus()
                            == dag.getEdge(fromVertex, toVertex).getExpectStatus());
        } else {
            throw new IllegalStateException("Can't handle precondition status: " + precondition);
        }
    }

    public static Set<JobVertex> getExecutableVertices(DAG<Long, JobVertex, JobEdge> dag) {
        // Return the beginning vertices, if there are any not executed.
        Collection<JobVertex> beginVertices = dag.getBeginVertices();
        Set<JobVertex> executableSet = beginVertices.stream()
                .filter(jobVertex -> jobVertex.getJobRunStatus() == null)
                .collect(toSet());

        if (CollectionUtils.isNotEmpty(executableSet)) {
            return executableSet;
        }

        return getNextExecutableVertices(beginVertices, dag);
    }

    private static Set<JobVertex> getNextExecutableVertices(
            Collection<JobVertex> fromVertices, DAG<Long, JobVertex, JobEdge> dag) {

        // Get the edges whose status matched his formVertex's status.
        Set<JobEdge> statusMatchedEdgeSet = fromVertices.stream()
                .flatMap(fromVertex -> dag.getEdgesFromVertex(fromVertex).stream()
                        .map(edge -> edge.unwrap(JobEdge.class))
                        .filter(edge -> edge.getExpectStatus() == fromVertex.getJobRunStatus()))
                .collect(toSet());

        // Get the executable vertices.
        Set<JobVertex> executableToVertices = statusMatchedEdgeSet.stream()
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

    private static ExecutionStatus determineFailureStatus(DAG<Long, JobVertex, JobEdge> dag) {
        // Whether each failure job has failure status handling vertex.
        return dag.getVertices().stream()
                        .filter(fromVertex -> fromVertex.getJobRunStatus() == FAILURE)
                        .anyMatch(fromVertex -> dag.getEdgesFromVertex(fromVertex).stream()
                                .noneMatch(jobEdge -> jobEdge.getExpectStatus() == fromVertex.getJobRunStatus()))
                ? FAILURE
                : EXPECTED_FAILURE;
    }
}
