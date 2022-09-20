package com.flink.platform.web.command.shell;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.ShellJob;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.JobCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** shell command builder. */
@Slf4j
@Component("shellCommandBuilder")
public class ShellCommandBuilder implements CommandBuilder {

    @Override
    public boolean isSupported(JobType jobType, String version) {
        return jobType == JobType.SHELL;
    }

    @Override
    public JobCommand buildCommand(Long flowRunId, JobRunInfo jobRunInfo) {
        ShellJob unwrap = jobRunInfo.getConfig().unwrap(ShellJob.class);
        return new ShellCommand(unwrap.getTimeout(), jobRunInfo.getSubject());
    }
}
