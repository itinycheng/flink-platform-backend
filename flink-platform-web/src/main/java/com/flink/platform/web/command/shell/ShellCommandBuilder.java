package com.flink.platform.web.command.shell;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.util.FileUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.environment.DispatcherService;
import com.flink.platform.web.service.JobRunExtraService;
import com.flink.platform.web.service.StorageService;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

import static com.flink.platform.common.enums.JobType.SHELL;
import static com.flink.platform.web.util.CommandUtil.commandType;
import static com.flink.platform.web.util.CommandUtil.getShellCommand;

/** shell command builder. */
@Slf4j
@Component("shellCommandBuilder")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ShellCommandBuilder implements CommandBuilder {

    private final StorageService storageService;

    private final DispatcherService dispatcherService;

    private final JobRunExtraService jobRunExtraService;

    @Override
    public boolean isSupported(JobType jobType, String version) {
        return jobType == SHELL;
    }

    @Override
    public JobCommand buildCommand(Long flowRunId, @Nonnull JobRunInfo jobRun) throws IOException {
        var storageFilePath = jobRunExtraService.buildStoragePath(jobRun, commandType());
        storageService.createFile(storageFilePath, jobRun.getSubject(), true);

        var commandFilePath = dispatcherService.buildLocalEnvFilePath(jobRun, commandType());
        var commandPath = Path.of(commandFilePath);
        FileUtil.rewriteFile(commandPath, jobRun.getSubject());
        FileUtil.setPermissions(commandPath, "rwxr--r--");

        var shellCommand = new ShellCommand(jobRun.getId(), null, getShellCommand(commandFilePath));
        populateTimeout(shellCommand, jobRun);
        return shellCommand;
    }
}
