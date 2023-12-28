package com.flink.platform.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
    ABNORMAL(5, TerminalState.TERMINAL),

    /** Internal status for job run only. */
    ERROR(6, TerminalState.TERMINAL),
    NOT_EXIST(7, TerminalState.TERMINAL),
    CREATED(8, TerminalState.NON_TERMINAL),
    KILLABLE(9, TerminalState.NON_TERMINAL),

    /** ! Only for jobFlow final status. */
    EXPECTED_FAILURE(10, TerminalState.TERMINAL);

    private enum TerminalState {
        TERMINAL,
        NON_TERMINAL
    }

    private static final List<ExecutionStatus> NON_TERMINALS = Arrays.stream(values())
            .filter(executionStatus -> executionStatus.terminalState == TerminalState.NON_TERMINAL)
            .collect(Collectors.toList());

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

    public static boolean isStopFlowState(ExecutionStatus status) {
        return status == ERROR || status == NOT_EXIST;
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
}
