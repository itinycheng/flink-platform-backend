package com.flink.platform.web.command.shell;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.util.FileUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.JobCommand;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.flink.platform.common.constants.Constant.DOT;
import static com.flink.platform.common.constants.Constant.OS_FILE_SEPARATOR;
import static com.flink.platform.common.enums.JobType.SHELL;
import static com.flink.platform.web.util.CommandUtil.commandType;
import static com.flink.platform.web.util.CommandUtil.getShellCommand;
import static com.flink.platform.web.util.PathUtil.getExecJobDirPath;

/** shell command builder. */
@Slf4j
@Component("shellCommandBuilder")
public class ShellCommandBuilder implements CommandBuilder {

    @Override
    public boolean isSupported(JobType jobType, String version) {
        return jobType == SHELL;
    }

    @Override
    public JobCommand buildCommand(Long flowRunId, @Nonnull JobRunInfo jobRun) throws IOException {
        String commandDirPath = getExecJobDirPath(jobRun.getUserId(), jobRun.getJobId(), SHELL);
        String commandFileName = String.join(DOT, jobRun.getJobId().toString(), commandType());
        String commandFilePath = String.join(OS_FILE_SEPARATOR, commandDirPath, commandFileName);
        Path commandPath = Paths.get(commandFilePath);
        FileUtil.rewriteFile(commandPath, jobRun.getSubject());
        FileUtil.setPermissions(commandPath, "rwxr--r--");

        ShellCommand shellCommand = new ShellCommand(jobRun.getId(), null, getShellCommand(commandFilePath));
        populateTimeout(shellCommand, jobRun);
        return shellCommand;
    }
}
