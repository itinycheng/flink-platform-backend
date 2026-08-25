package com.flink.platform.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.exception.UnrecoverableException;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.grpc.KillJobRequest;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.grpc.JobGrpcClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.flink.platform.common.enums.ExecutionStatus.KILLED;
import static com.flink.platform.common.enums.ExecutionStatus.KILLING;
import static com.flink.platform.common.enums.ExecutionStatus.getNonTerminals;

/** Kill job service. */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class KillJobService {

    private final JobGrpcClient jobGrpcClient;

    private final JobRunInfoService jobRunService;

    private final List<CommandExecutor> jobCommandExecutors;

    private final JobFlowRunService jobFlowRunService;

    public void forceKillFlowRun(Long flowRunId) {
        killFlowRun(flowRunId);

        var newFlowRun = new JobFlowRun();
        newFlowRun.setId(flowRunId);
        newFlowRun.setStatus(KILLED);
        newFlowRun.setEndTime(LocalDateTime.now());
        jobFlowRunService.updateById(newFlowRun);
    }

    public boolean killFlowRun(Long flowRunId) {
        var runList = jobRunService.listLiteNonTerminalRuns(flowRunId);
        if (CollectionUtils.isEmpty(runList)) {
            jobFlowRunService.updateStatusById(flowRunId, KILLED);
            return true;
        }

        // kill remote runs.
        jobFlowRunService.updateStatusById(flowRunId, KILLING);
        return runList.parallelStream()
                .map(this::attemptToKillJob)
                .reduce((bool1, bool2) -> bool1 && bool2)
                .orElse(false);
    }

    public boolean attemptToKillJob(JobRunInfo jobRun) {
        try {
            return killRemoteJob(jobRun);
        } catch (Exception e) {
            log.error("kill job: {} failed.", jobRun.getId(), e);
            return false;
        }
    }

    public void killJob(final long jobRunId) {
        var jobRun = jobRunService.getOne(new QueryWrapper<JobRunInfo>()
                .lambda()
                .select(JobRunInfo::getType)
                .eq(JobRunInfo::getId, jobRunId)
                .in(JobRunInfo::getStatus, getNonTerminals()));
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

        jobRun = jobRunService.getOne(new QueryWrapper<JobRunInfo>()
                .lambda()
                .select(JobRunInfo::getStatus)
                .eq(JobRunInfo::getId, jobRunId));

        // set status to KILLED
        // Better in a transaction with serializable isolation level.
        if (!jobRun.getStatus().isTerminalState()) {
            var newJobRun = new JobRunInfo();
            newJobRun.setId(jobRunId);
            newJobRun.setStatus(KILLED);
            newJobRun.setEndTime(LocalDateTime.now());
            jobRunService.updateById(newJobRun);
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
