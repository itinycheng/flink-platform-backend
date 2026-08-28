package com.flink.platform.dao.util;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.graph.DAG;
import com.flink.platform.common.model.JobEdge;
import com.flink.platform.common.model.JobVertex;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import static com.flink.platform.common.enums.ExecutionStatus.ABNORMAL;
import static com.flink.platform.common.enums.ExecutionStatus.ERROR;
import static com.flink.platform.common.enums.ExecutionStatus.EXPECTED_FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE_STATUSES;
import static com.flink.platform.common.enums.ExecutionStatus.KILLED;
import static com.flink.platform.common.enums.ExecutionStatus.NOT_EXIST;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.common.enums.ExecutionStatus.isFailure;
import static java.util.stream.Collectors.toSet;

/** Dag helper for job flow. */
public class JobFlowDagHelper {

    public static ExecutionStatus getFinalStatus(DAG<Long, JobVertex, JobEdge> dag) {
        var statusSet = dag.getVertices().stream()
                .map(JobVertex::getJobRunStatus)
                .filter(Objects::nonNull)
                .collect(toSet());
        return statusSet.stream()
                .filter(status -> !status.isTerminalState())
                .findFirst()
                .orElseGet(() -> {
                    if (FAILURE_STATUSES.stream().anyMatch(statusSet::contains)) {
                        var status = determineFailureStatus(dag);
                        if (EXPECTED_FAILURE.equals(status)) {
                            return status;
                        }
                    }

                    if (statusSet.contains(KILLED)) {
                        return KILLED;
                    } else if (statusSet.contains(FAILURE)
                            || statusSet.contains(ERROR)
                            || statusSet.contains(NOT_EXIST)
                            || statusSet.contains(ABNORMAL)) {
                        return FAILURE;
                    } else if (statusSet.contains(SUCCESS)) {
                        return SUCCESS;
                    } else {
                        throw new IllegalStateException("Unreachable code reached.");
                    }
                });
    }

    public static boolean hasUnexecutedVertices(DAG<Long, JobVertex, JobEdge> dag) {
        // Check whether there are vertices in non-terminal status.
        var vertices = dag.getVertices();
        if (vertices.stream()
                .map(JobVertex::getJobRunStatus)
                .filter(Objects::nonNull)
                .anyMatch(executionStatus -> !executionStatus.isTerminalState())) {
            return true;
        }

        // Check whether there are vertices not executed.
        var beginVertices = dag.getBeginVertices();
        if (beginVertices.stream().anyMatch(vertex -> vertex.getJobRunStatus() == null)) {
            return true;
        }

        return CollectionUtils.isNotEmpty(getNextExecutableVertices(beginVertices, dag));
    }

    public static boolean isPreconditionSatisfied(JobVertex toVertex, DAG<Long, JobVertex, JobEdge> dag) {
        var preVertices = dag.getPreVertices(toVertex);
        if (CollectionUtils.isEmpty(preVertices)) {
            return true;
        }

        Predicate<JobVertex> qualifies = from -> upstreamQualifies(from, dag.getEdge(from, toVertex), toVertex);
        return toVertex.getPrecondition().requiresAllQualified()
                ? preVertices.stream().allMatch(qualifies)
                : preVertices.stream().anyMatch(qualifies);
    }

    public static Set<JobVertex> getExecutableVertices(DAG<Long, JobVertex, JobEdge> dag) {
        // Return the beginning vertices, if there are any not executed.
        var beginVertices = dag.getBeginVertices();
        var executableSet = beginVertices.stream()
                .filter(jobVertex -> jobVertex.getJobRunStatus() == null)
                .collect(toSet());

        if (CollectionUtils.isNotEmpty(executableSet)) {
            return executableSet;
        }

        return getNextExecutableVertices(beginVertices, dag);
    }

    // ====================================================
    // ====================== private =====================
    // ====================================================

    private static Set<JobVertex> getNextExecutableVertices(
            Collection<JobVertex> fromVertices, DAG<Long, JobVertex, JobEdge> dag) {

        // Outgoing edges whose source (from) vertex qualifies under the target's trigger rule.
        var triggeredEdges = fromVertices.stream()
                .flatMap(from -> dag.getEdgesFromVertex(from).stream()
                        .map(edge -> edge.unwrap(JobEdge.class))
                        .filter(edge -> upstreamQualifies(from, edge, dag.getVertex(edge.getToVId()))))
                .collect(toSet());

        // Their target vertices, kept only if the target's full precondition is satisfied.
        var executableVertices = triggeredEdges.stream()
                .map(edge -> dag.getVertex(edge.getToVId()))
                .filter(toVertex -> isPreconditionSatisfied(toVertex, dag))
                .collect(toSet());

        // If toVertex is executed, use it as fromVertex to find the next executable vertex.
        var executedVertices = new HashSet<JobVertex>();
        var unexecutedVertices = new HashSet<JobVertex>();
        for (var executableVertex : executableVertices) {
            if (executableVertex.getJobRunStatus() != null) {
                executedVertices.add(executableVertex);
            } else {
                unexecutedVertices.add(executableVertex);
            }
        }

        if (CollectionUtils.isNotEmpty(executedVertices)) {
            unexecutedVertices.addAll(getNextExecutableVertices(executedVertices, dag));
        }

        return unexecutedVertices;
    }

    private static ExecutionStatus determineFailureStatus(DAG<Long, JobVertex, JobEdge> dag) {
        // Whether each failure job has failure status handling vertex.
        return dag.getVertices().stream()
                        .filter(from -> isFailure(from.getJobRunStatus()))
                        .anyMatch(from -> dag.getEdgesFromVertex(from).stream()
                                .noneMatch(jobEdge -> FAILURE.equals(jobEdge.getExpectStatus())))
                ? FAILURE
                : EXPECTED_FAILURE;
    }

    private static boolean upstreamQualifies(JobVertex from, JobEdge edge, JobVertex toVertex) {
        return toVertex.getPrecondition().requiresStatusMatched()
                ? statusEquals(edge.getExpectStatus(), from.getJobRunStatus())
                : hasTerminalState(from);
    }

    private static boolean hasTerminalState(JobVertex vertex) {
        var status = vertex.getJobRunStatus();
        return status != null && status.isTerminalState();
    }

    private static boolean statusEquals(ExecutionStatus expectedStatus, ExecutionStatus jobStatus) {
        return switch (expectedStatus) {
            case SUCCESS -> jobStatus == SUCCESS;
            case FAILURE -> isFailure(jobStatus);
            default -> false;
        };
    }
}
