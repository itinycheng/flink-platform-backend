package com.flink.platform.web.command.shell;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.util.DurationUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.ShellJob;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.JobCommand;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

import java.time.Duration;

/** shell command builder. */
@Slf4j
@Component("shellCommandBuilder")
public class ShellCommandBuilder implements CommandBuilder {

    @Override
    public boolean isSupported(JobType jobType, String version) {
        return jobType == JobType.SHELL;
    }

    @Override
    public JobCommand buildCommand(Long flowRunId, @Nonnull JobRunInfo jobRunInfo) {
        ShellJob unwrap = jobRunInfo.getConfig().unwrap(ShellJob.class);
        String timeoutExpression = unwrap.getTimeout();
        long timeout;
        if (NumberUtils.isCreatable(timeoutExpression)) {
            timeout = NumberUtils.createNumber(timeoutExpression).longValue();
        } else {
            Duration duration = DurationUtil.parse(timeoutExpression);
            timeout = duration.toMillis();
        }

        return new ShellCommand(jobRunInfo.getId(), timeout, jobRunInfo.getSubject());
    }
}
