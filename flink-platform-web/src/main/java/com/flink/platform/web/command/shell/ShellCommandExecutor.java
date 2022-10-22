package com.flink.platform.web.command.shell;

import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCallback;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.util.ShellCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.KILLABLE;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.web.util.CommandUtil.EXIT_CODE_SUCCESS;
import static com.flink.platform.web.util.CommandUtil.forceKill;

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
        ShellTask task =
                new ShellTask(
                        shellCommand.getJobRunId(),
                        shellCommand.getScript(),
                        null,
                        Math.min(
                                workerConfig.getMaxShellExecTimeoutMills(),
                                shellCommand.getTimeout()));
        shellCommand.setTask(task);
        task.run();

        ShellCallback callback = task.buildShellCallback();
        return new JobCallback(
                callback,
                null,
                callback.isExited() && callback.getExitCode() == EXIT_CODE_SUCCESS
                        ? SUCCESS
                        : (callback.isExited() ? FAILURE : KILLABLE));
    }

    @Override
    public void killCommand(JobCommand jobCommand) {
        ShellTask task = jobCommand.getTask().unwrap(ShellTask.class);
        if (task == null) {
            return;
        }

        Integer processId = task.getProcessId();
        if (processId != null && processId > 0) {
            forceKill(processId, null);
        }
    }
}
