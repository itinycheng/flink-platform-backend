package com.flink.platform.web.command.dependent;

import com.flink.platform.web.command.JobCommand;
import lombok.Getter;

/** condition command. */
@Getter
public class DependentCommand extends JobCommand {

    /** whether the dependent verification is successful. */
    private final boolean success;

    private final String message;

    public DependentCommand(long jobRunId, boolean success, String message) {
        super(jobRunId);
        this.success = success;
        this.message = message;
    }

    @Override
    public String toCommandString() {
        return "do nothing";
    }
}
