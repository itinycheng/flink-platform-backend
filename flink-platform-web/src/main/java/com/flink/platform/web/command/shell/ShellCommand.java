package com.flink.platform.web.command.shell;

import com.flink.platform.web.command.JobCommand;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** shell command. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShellCommand implements JobCommand {

    private long timeout;

    private String script;

    @Override
    public String toCommandString() {
        return script;
    }
}
