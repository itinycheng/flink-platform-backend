package com.flink.platform.web.command.shell;

import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCallback;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.util.CommandCallback;
import com.flink.platform.web.util.CommandUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.web.util.CommandCallback.EXIT_CODE_SUCCESS;

/** shell command executor. */
@Slf4j
@Component("shellCommandExecutor")
public class ShellCommandExecutor implements CommandExecutor {

    @Autowired private WorkerConfig workerConfig;

    @Override
    public boolean isSupported(JobCommand jobCommand) {
        return jobCommand instanceof ShellCommand;
    }

    @Nonnull
    @Override
    public JobCallback execCommand(JobCommand command) throws Exception {
        ShellCommand shellCommand = (ShellCommand) command;
        CommandCallback callback =
                CommandUtil.exec(
                        shellCommand.getScript(),
                        null,
                        Math.min(
                                workerConfig.getMaxShellExecTimeoutMills(),
                                shellCommand.getTimeout()));

        return new JobCallback(
                null,
                null,
                callback.getMessage(),
                callback.isExited() && callback.getExitCode() == EXIT_CODE_SUCCESS
                        ? SUCCESS
                        : FAILURE);
    }
}
