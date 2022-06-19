package com.flink.platform.web.command.shell;

import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCallback;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.util.CommandCallback;
import com.flink.platform.web.util.CommandUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;

/** shell command executor. */
@Slf4j
@Component("shellCommandExecutor")
public class ShellCommandExecutor implements CommandExecutor {

    @Override
    public boolean isSupported(JobCommand jobCommand) {
        return jobCommand instanceof ShellCommand;
    }

    @Override
    public JobCallback execCommand(JobCommand command) throws Exception {
        ShellCommand shellCommand = (ShellCommand) command;
        CommandCallback callback =
                CommandUtil.exec(shellCommand.getScript(), null, shellCommand.getTimeout());

        return new JobCallback(
                null, null, callback.getMessage(), callback.isSuccess() ? SUCCESS : FAILURE);
    }
}
