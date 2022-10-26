package com.flink.platform.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.exception.UnrecoverableException;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.flink.platform.common.enums.ExecutionStatus.KILLED;
import static com.flink.platform.common.enums.ExecutionStatus.getNonTerminals;

/** Kill job service. */
@Slf4j
@Service
public class KillJobService {

    private final JobRunInfoService jobRunInfoService;

    private final List<CommandExecutor> jobCommandExecutors;

    @Autowired
    public KillJobService(
            JobRunInfoService jobRunInfoService, List<CommandExecutor> jobCommandExecutors) {
        this.jobRunInfoService = jobRunInfoService;
        this.jobCommandExecutors = jobCommandExecutors;
    }

    public void killJob(final long jobRunId) {
        JobRunInfo jobRunInfo =
                jobRunInfoService.getOne(
                        new QueryWrapper<JobRunInfo>()
                                .lambda()
                                .eq(JobRunInfo::getId, jobRunId)
                                .in(JobRunInfo::getStatus, getNonTerminals()));
        if (jobRunInfo == null) {
            return;
        }

        // exec kill
        jobCommandExecutors.stream()
                .filter(executor -> executor.isSupported(jobRunInfo.getType()))
                .findFirst()
                .orElseThrow(() -> new UnrecoverableException("No available job command executor"))
                .kill(jobRunId);

        // set status to KILLED
        JobRunInfo newJobRun = new JobRunInfo();
        newJobRun.setId(jobRunInfo.getId());
        newJobRun.setStatus(KILLED);
        newJobRun.setStopTime(LocalDateTime.now());
        jobRunInfoService.updateById(newJobRun);

        log.info("Kill job run: {} finished, time: {}", jobRunId, System.currentTimeMillis());
    }
}
