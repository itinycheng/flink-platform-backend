package com.flink.platform.web.command.shell;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.util.DurationUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.ShellJob;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.util.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;

import static com.flink.platform.common.constants.Constant.DOT;
import static com.flink.platform.common.constants.Constant.PATH_SEPARATOR;
import static com.flink.platform.web.util.CommandUtil.commandType;
import static com.flink.platform.web.util.CommandUtil.getShellCommand;
import static com.flink.platform.web.util.EnvPathUtil.getExecJobDirPath;

/** shell command builder. */
@Slf4j
@Component("shellCommandBuilder")
public class ShellCommandBuilder implements CommandBuilder {

    @Override
    public boolean isSupported(JobType jobType, String version) {
        return jobType == JobType.SHELL;
    }

    @Override
    public JobCommand buildCommand(Long flowRunId, @Nonnull JobRunInfo jobRun) throws IOException {
        ShellJob unwrap = jobRun.getConfig().unwrap(ShellJob.class);
        String timeoutExpression = unwrap.getTimeout();
        long timeout;
        if (NumberUtils.isCreatable(timeoutExpression)) {
            // Compatible with old version.
            timeout = NumberUtils.createNumber(timeoutExpression).longValue();
        } else {
            Duration duration = DurationUtil.parse(timeoutExpression);
            timeout = duration.toMillis();
        }

        String commandDirPath = getExecJobDirPath(jobRun.getUserId(), jobRun.getJobId());
        String commandFileName = String.join(DOT, jobRun.getJobId().toString(), commandType());
        String commandFilePath = String.join(PATH_SEPARATOR, commandDirPath, commandFileName);
        FileUtil.writeToFile(Paths.get(commandFilePath), jobRun.getSubject());
        return new ShellCommand(jobRun.getId(), timeout, null, getShellCommand(commandFilePath));
    }
}
