package com.flink.platform.web.service;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.exception.UnrecoverableException;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.command.flink.FlinkCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

import static com.flink.platform.common.constants.Constant.HOST_IP;

/** Process job service. */
@Slf4j
@Service
public class ProcessJobService {

    private final JobRunInfoService jobRunInfoService;

    private final List<CommandBuilder> jobCommandBuilders;

    private final List<CommandExecutor> jobCommandExecutors;

    @Autowired
    public ProcessJobService(
            JobRunInfoService jobRunInfoService,
            List<CommandBuilder> jobCommandBuilders,
            List<CommandExecutor> jobCommandExecutors) {
        this.jobRunInfoService = jobRunInfoService;
        this.jobCommandBuilders = jobCommandBuilders;
        this.jobCommandExecutors = jobCommandExecutors;
    }

    public void processJob(final long jobRunId) throws Exception {
        JobCommand jobCommand = null;
        JobRunInfo jobRunInfo = null;

        try {
            // step 1: get job info
            jobRunInfo = jobRunInfoService.getById(jobRunId);
            if (jobRunInfo == null) {
                throw new UnrecoverableException(String.format("The job run: %s is no longer exists.", jobRunId));
            }

            // step 2: Update jobRun info before execution.
            JobRunInfo newJobRun = new JobRunInfo();
            newJobRun.setId(jobRunInfo.getId());
            newJobRun.setHost(HOST_IP);
            jobRunInfoService.updateById(newJobRun);

            JobType jobType = jobRunInfo.getType();
            String version = jobRunInfo.getVersion();

            // step 3: build job command, create a SqlContext if needed
            jobCommand = jobCommandBuilders.stream()
                    .filter(builder -> builder.isSupported(jobType, version))
                    .findFirst()
                    .orElseThrow(() -> new UnrecoverableException("No available job command builder"))
                    .buildCommand(jobRunInfo.getFlowRunId(), jobRunInfo);

            // step 4: submit job
            LocalDateTime submitTime = LocalDateTime.now();
            final JobCommand command = jobCommand;
            JobCallback callback = jobCommandExecutors.stream()
                    .filter(executor -> executor.isSupported(jobType))
                    .findFirst()
                    .orElseThrow(() -> new UnrecoverableException("No available job command executor"))
                    .exec(command);

            // step 5: write job run info to db
            ExecutionStatus executionStatus = callback.getStatus();
            newJobRun = new JobRunInfo();
            newJobRun.setId(jobRunInfo.getId());
            newJobRun.setStatus(executionStatus);
            newJobRun.setBackInfo(callback);
            newJobRun.setSubmitTime(submitTime);
            if (executionStatus.isTerminalState()) {
                newJobRun.setStopTime(LocalDateTime.now());
            }
            jobRunInfoService.updateById(newJobRun);

            // step 6: print job command info
            log.info("Job run: {} submitted, time: {}", jobRunId, System.currentTimeMillis());

        } finally {
            if (jobRunInfo != null && jobRunInfo.getType() == JobType.FLINK_SQL && jobCommand != null) {
                try {
                    FlinkCommand flinkCommand = (FlinkCommand) jobCommand;
                    if (flinkCommand.getMainArgs() != null) {
                        Files.deleteIfExists(Paths.get(flinkCommand.getMainArgs()));
                    }
                } catch (Exception e) {
                    log.warn("Delete sql context file failed", e);
                }
            }
        }
    }
}
