package com.flink.platform.common.enums;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * Execution status enums, used for Job and JobFlow. <br>
 * ! Check if JobFlowDagHelper::getFinalStatus is correct when adding/removing terminal status.
 */
public enum ExecutionStatus {
    SUBMITTED(0, TerminalState.NON_TERMINAL),
    RUNNING(1, TerminalState.NON_TERMINAL),
    SUCCESS(2, TerminalState.TERMINAL),
    FAILURE(3, TerminalState.TERMINAL),
    KILLED(4, TerminalState.TERMINAL),

    @Deprecated
    ABNORMAL(5, TerminalState.TERMINAL),

    /** Internal status for job run only; a flow run converges it to FAILURE. */
    ERROR(6, TerminalState.TERMINAL),

    @Deprecated
    NOT_EXIST(7, TerminalState.TERMINAL),
    CREATED(8, TerminalState.NON_TERMINAL),

    @JsonAlias("KILLABLE")
    KILLING(9, TerminalState.TRANSIENT),

    /** ! Only for jobFlowRun: final status. */
    EXPECTED_FAILURE(10, TerminalState.TERMINAL),

    /** ! Only for jobFlowRun: waiting for prerequisite flow dependencies to be satisfied. */
    WAITING(11, TerminalState.NON_TERMINAL);

    public static final Set<ExecutionStatus> FAILURE_STATUSES =
            Stream.of(FAILURE, KILLED, ABNORMAL, ERROR, NOT_EXIST).collect(toSet());

    public static final List<ExecutionStatus> NON_TERMINALS = Arrays.stream(values())
            .filter(executionStatus -> executionStatus.terminalState != TerminalState.TERMINAL)
            .collect(toList());

    public static final List<ExecutionStatus> EXECUTABLE_STATUSES =
            NON_TERMINALS.stream().filter(status -> status != WAITING).collect(toList());

    @Getter
    private final int code;

    private final TerminalState terminalState;

    ExecutionStatus(int code, TerminalState terminalState) {
        this.code = code;
        this.terminalState = terminalState;
    }

    public boolean isTerminalState() {
        return terminalState == TerminalState.TERMINAL;
    }

    public boolean isTransient() {
        return terminalState == TerminalState.TRANSIENT;
    }

    public static List<ExecutionStatus> getNonTerminals() {
        return NON_TERMINALS;
    }

    public static ExecutionStatus from(Integer code) {
        for (ExecutionStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }

        throw new IllegalArgumentException("Unknown execution status code: " + code);
    }

    public static boolean isFailure(ExecutionStatus status) {
        return FAILURE_STATUSES.contains(status);
    }

    private enum TerminalState {
        TERMINAL,
        NON_TERMINAL,
        TRANSIENT
    }
}
