package com.flink.platform.web.command.condition;

import com.flink.platform.web.command.JobCommand;
import lombok.Getter;

/** condition command. */
@Getter
public class ConditionCommand extends JobCommand {

    private final boolean success;

    public ConditionCommand(long jobRunId, Long flowRunId, boolean success) {
        super(jobRunId, flowRunId);
        this.success = success;
    }

    @Override
    public String toCommandString() {
        return "do nothing";
    }
}
