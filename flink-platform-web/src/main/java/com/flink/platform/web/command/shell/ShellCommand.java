package com.flink.platform.web.command.shell;

import com.flink.platform.web.command.JobCommand;
import lombok.Getter;

/** shell command. */
@Getter
public class ShellCommand extends JobCommand {

    private final long timeout;

    private final String[] envs;

    private final String script;

    public ShellCommand(long jobRunId, long timeout, String[] envs, String script) {
        super(jobRunId);
        this.timeout = timeout;
        this.envs = envs;
        this.script = script;
    }

    @Override
    public String toCommandString() {
        return script;
    }
}
