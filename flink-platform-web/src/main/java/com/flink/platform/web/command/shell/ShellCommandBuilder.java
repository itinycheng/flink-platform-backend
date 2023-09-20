package com.flink.platform.web.command.shell;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.util.DurationUtil;
import com.flink.platform.common.util.FileUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.ShellJob;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.JobCommand;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import static com.flink.platform.common.constants.Constant.DOT;
import static com.flink.platform.common.constants.Constant.FILE_SEPARATOR;
import static com.flink.platform.web.util.CommandUtil.commandType;
import static com.flink.platform.web.util.CommandUtil.getShellCommand;
import static com.flink.platform.web.util.PathUtil.getExecJobDirPath;

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
        String commandFilePath = String.join(FILE_SEPARATOR, commandDirPath, commandFileName);
        Path commandPath = Paths.get(commandFilePath);
        FileUtil.rewriteFile(commandPath, jobRun.getSubject());
        FileUtil.setPermissions(commandPath, "rwxr--r--");
        return new ShellCommand(jobRun.getId(), timeout, null, getShellCommand(commandFilePath));
    }
}
