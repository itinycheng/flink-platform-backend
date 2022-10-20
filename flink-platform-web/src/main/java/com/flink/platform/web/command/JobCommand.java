package com.flink.platform.web.command;

import lombok.Data;

/** job command. */
@Data
public abstract class JobCommand {

    protected final long jobRunId;

    protected AbstractTask task;

    /**
     * build a command.
     *
     * @return command to execute.
     */
    public abstract String toCommandString();
}
