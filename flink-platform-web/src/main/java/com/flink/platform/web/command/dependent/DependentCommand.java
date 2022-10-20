package com.flink.platform.web.command.dependent;

import com.flink.platform.web.command.JobCommand;
import lombok.Getter;

/** condition command. */
@Getter
public class DependentCommand extends JobCommand {

    /** whether the dependent verification is successful. */
    private final boolean success;

    public DependentCommand(long jobRunId, boolean success) {
        super(jobRunId);
        this.success = success;
    }

    @Override
    public String toCommandString() {
        return "do nothing";
    }
}
