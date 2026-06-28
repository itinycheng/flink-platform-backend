package com.flink.platform.web.command.shell;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.config.WorkerConfig;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** shell command executor. */
@Slf4j
@Component("shellCommandExecutor")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ShellCommandExecutor implements CommandExecutor {

    private final WorkerConfig workerConfig;

    private final JobRunInfoService jobRunInfoService;

    @Override
    public boolean isSupported(JobType jobType) {
        return jobType == JobType.SHELL;
    }

    @Nonnull
    @Override
    public JobCallback execCommand(@Nonnull JobCommand command) throws Exception {
        var shellCommand = (ShellCommand) command;
        var timeout = shellCommand.getTimeout();
        var task = new ShellTask(
                shellCommand.getJobRunId(),
                shellCommand.getScript(),
                shellCommand.getEnvp(),
                timeout != null
                        ? Math.min(workerConfig.getMaxShellExecTimeoutMills(), timeout.toMillis())
                        : workerConfig.getMaxShellExecTimeoutMills());
        shellCommand.setTask(task);
        task.run();

        return new JobCallback(task.buildShellCallback(), null, task.finalStatus());
    }

    @Override
    public void killCommand(@Nonnull JobCommand command) {
        var task = command.getTask();
        if (task == null) {
            var jobRun = jobRunInfoService.getById(command.getJobRunId());
            var jobCallback = jobRun.getBackInfo();
            if (!jobRun.getStatus().isTerminalState() && jobCallback != null) {
                task = new ShellTask(jobRun.getId(), jobCallback.getProcessId());
            }
        }

        if (task != null) {
            task.cancel();
        }
    }
}
