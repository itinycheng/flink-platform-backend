package com.flink.platform.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.exception.UnrecoverableException;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.grpc.KillJobRequest;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.grpc.JobGrpcClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.flink.platform.common.enums.ExecutionStatus.KILLED;
import static com.flink.platform.common.enums.ExecutionStatus.UNFINISHED_STATUSES;

/** Kill job service. */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class KillJobService {

    private final JobGrpcClient jobGrpcClient;

    private final JobRunInfoService jobRunService;

    private final List<CommandExecutor> jobCommandExecutors;

    public boolean attemptToKillJob(JobRunInfo jobRun) {
        try {
            return killRemoteJob(jobRun);
        } catch (Exception e) {
            log.error("Failed to request job-run kill, jobRunId={}", jobRun.getId(), e);
            return false;
        }
    }

    public void killJob(final long jobRunId) {
        var jobRun = jobRunService.getOne(new QueryWrapper<JobRunInfo>()
                .lambda()
                .select(JobRunInfo::getType)
                .eq(JobRunInfo::getId, jobRunId)
                .in(JobRunInfo::getStatus, UNFINISHED_STATUSES));
        if (jobRun == null) {
            return;
        }

        // exec kill
        var type = jobRun.getType();
        jobCommandExecutors.stream()
                .filter(executor -> executor.isSupported(type))
                .findFirst()
                .orElseThrow(() -> new UnrecoverableException("No available job command executor"))
                .kill(jobRunId);

        var update = new JobRunInfo();
        update.setStatus(KILLED);
        update.setEndTime(LocalDateTime.now());
        if (jobRunService.updateStatusFromAnyTo(jobRunId, UNFINISHED_STATUSES, update) == 1) {
            log.info("Kill job run: {} finished, time: {}", jobRunId, System.currentTimeMillis());
        }
    }

    // ================== Private Methods ==================

    private boolean killRemoteJob(JobRunInfo jobRun) {
        var host = jobRun.getHost();
        var stub = jobGrpcClient.grpcClient(host);
        var request = KillJobRequest.newBuilder().setJobRunId(jobRun.getId()).build();
        var reply = stub.killJob(request);
        return reply != null;
    }
}
