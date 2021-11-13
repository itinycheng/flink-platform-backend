package com.flink.platform.common.enums;

import java.util.ArrayList;
import java.util.List;

/** job yarn status enums. */
public enum ExecutionStatus {
    UNDEFINED(-1, TerminalState.NON_TERMINAL),
    SUBMITTED(0, TerminalState.NON_TERMINAL),
    RUNNING(1, TerminalState.NON_TERMINAL),
    SUCCEEDED(2, TerminalState.TERMINAL),
    FAILED(3, TerminalState.TERMINAL),
    KILLED(4, TerminalState.TERMINAL),
    ABNORMAL(5, TerminalState.TERMINAL);

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

    public static List<ExecutionStatus> getNonTerminals() {
        List<ExecutionStatus> statusList = new ArrayList<>(values().length);
        for (ExecutionStatus value : values()) {
            if (value.terminalState == TerminalState.NON_TERMINAL) {
                statusList.add(value);
            }
        }
        return statusList;
    }
}
