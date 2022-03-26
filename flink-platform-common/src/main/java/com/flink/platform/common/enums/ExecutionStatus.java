package com.flink.platform.common.enums;

import java.util.ArrayList;
import java.util.List;

/** Execution status enums, used for Job and JobFlow. */
public enum ExecutionStatus {
    SUBMITTED(0, TerminalState.NON_TERMINAL),
    RUNNING(1, TerminalState.NON_TERMINAL),
    SUCCESS(2, TerminalState.TERMINAL),
    FAILURE(3, TerminalState.TERMINAL),
    KILLED(4, TerminalState.TERMINAL),
    ABNORMAL(5, TerminalState.TERMINAL),

    /** Internal status for job run only. */
    ERROR(6, TerminalState.TERMINAL),
    NOT_EXIST(7, TerminalState.TERMINAL);

    private enum TerminalState {
        TERMINAL,
        NON_TERMINAL
    }

    private final int code;

    private final TerminalState terminalState;

    ExecutionStatus(int code, TerminalState terminalState) {
        this.code = code;
        this.terminalState = terminalState;
    }

    public boolean isTerminalState() {
        return terminalState == TerminalState.TERMINAL;
    }

    public int getCode() {
        return code;
    }

    public static List<ExecutionStatus> getTerminals() {
        List<ExecutionStatus> statusList = new ArrayList<>(values().length);
        for (ExecutionStatus value : values()) {
            if (value.terminalState == TerminalState.TERMINAL) {
                statusList.add(value);
            }
        }
        return statusList;
    }

    public static List<ExecutionStatus> getNonTerminals() {
        List<ExecutionStatus> statusList = new ArrayList<>(values().length);
        for (ExecutionStatus value : values()) {
            if (value.terminalState == TerminalState.NON_TERMINAL) {
                statusList.add(value);
            }
        }
        return statusList;
    }

    public boolean isErrTerminalState() {
        return this == FAILURE || this == KILLED || this == ABNORMAL;
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
