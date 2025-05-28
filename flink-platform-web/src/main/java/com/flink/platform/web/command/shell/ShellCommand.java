package com.flink.platform.web.command.shell;

import com.flink.platform.web.command.JobCommand;
import lombok.Getter;

import java.util.Map;

/** shell command. */
@Getter
public class ShellCommand extends JobCommand {

    private final Map<String, String> envp;

    private final String script;

    public ShellCommand(long jobRunId, Map<String, String> envp, String script) {
        super(jobRunId);
        this.envp = envp;
        this.script = script;
    }

    @Override
    public String toCommandString() {
        return script;
    }
}
