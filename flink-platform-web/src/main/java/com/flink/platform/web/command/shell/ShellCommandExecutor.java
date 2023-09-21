package com.flink.platform.web.command.shell;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.AbstractTask;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.config.WorkerConfig;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** shell command executor. */
@Slf4j
@Component("shellCommandExecutor")
public class ShellCommandExecutor implements CommandExecutor {

    @Autowired
    private WorkerConfig workerConfig;

    @Autowired
    private JobRunInfoService jobRunInfoService;

    @Override
    public boolean isSupported(JobType jobType) {
        return jobType == JobType.SHELL;
    }

    @Nonnull
    @Override
    public JobCallback execCommand(@Nonnull JobCommand command) throws Exception {
        ShellCommand shellCommand = (ShellCommand) command;
        ShellTask task = new ShellTask(
                shellCommand.getJobRunId(),
                shellCommand.getScript(),
                shellCommand.getEnvs(),
                Math.min(workerConfig.getMaxShellExecTimeoutMills(), shellCommand.getTimeout()));
        shellCommand.setTask(task);
        task.run();

        return new JobCallback(task.buildShellCallback(), null, task.finalStatus());
    }

    @Override
    public void killCommand(@Nonnull JobCommand command) {
        AbstractTask task = command.getTask();
        if (task == null) {
            JobRunInfo jobRun = jobRunInfoService.getById(command.getJobRunId());
            JobCallback jobCallback = jobRun.getBackInfo();
            if (!jobRun.getStatus().isTerminalState() && jobCallback != null) {
                task = new ShellTask(jobRun.getId(), jobCallback.getProcessId());
            }
        }

        if (task != null) {
            task.cancel();
        }
    }
}
