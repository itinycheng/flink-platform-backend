package com.flink.platform.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.exception.UnrecoverableException;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.grpc.JobGrpcServiceGrpc;
import com.flink.platform.grpc.KillJobRequest;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.grpc.JobProcessGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.flink.platform.common.enums.ExecutionStatus.KILLABLE;
import static com.flink.platform.common.enums.ExecutionStatus.KILLED;
import static com.flink.platform.common.enums.ExecutionStatus.getNonTerminals;

/** Kill job service. */
@Slf4j
@Service
public class KillJobService {

    private final JobProcessGrpcClient jobProcessGrpcClient;

    private final JobRunInfoService jobRunInfoService;

    private final List<CommandExecutor> jobCommandExecutors;

    private final JobFlowRunService jobFlowRunService;

    @Autowired
    public KillJobService(
            JobRunInfoService jobRunInfoService,
            List<CommandExecutor> jobCommandExecutors,
            JobProcessGrpcClient jobProcessGrpcClient,
            JobFlowRunService jobFlowRunService) {
        this.jobRunInfoService = jobRunInfoService;
        this.jobCommandExecutors = jobCommandExecutors;
        this.jobProcessGrpcClient = jobProcessGrpcClient;
        this.jobFlowRunService = jobFlowRunService;
    }

    public boolean killRemoteFlow(Long userId, Long flowRunId) {
        // Set the status of the workflow to KILLABLE.
        var newJobFlowRun = new JobFlowRun();
        newJobFlowRun.setId(flowRunId);
        newJobFlowRun.setStatus(KILLABLE);
        jobFlowRunService.updateById(newJobFlowRun);

        // Get unfinished jobs and kill them concurrently.
        List<JobRunInfo> list = jobRunInfoService.list(new QueryWrapper<JobRunInfo>()
                .lambda()
                .select(JobRunInfo::getId, JobRunInfo::getHost)
                .eq(JobRunInfo::getFlowRunId, flowRunId)
                .eq(JobRunInfo::getUserId, userId)
                .in(JobRunInfo::getStatus, getNonTerminals()));
        if (CollectionUtils.isEmpty(list)) {
            return true;
        }

        return list.parallelStream()
                .map(jobRun -> {
                    try {
                        return killRemoteJob(jobRun);
                    } catch (Exception e) {
                        log.error("kill job: {} failed.", jobRun.getId(), e);
                        return false;
                    }
                })
                .reduce((bool1, bool2) -> bool1 && bool2)
                .orElse(false);
    }

    public boolean killRemoteJob(JobRunInfo jobRun) {
        String host = jobRun.getHost();
        JobGrpcServiceGrpc.JobGrpcServiceBlockingStub stub = jobProcessGrpcClient.grpcClient(host);
        KillJobRequest request =
                KillJobRequest.newBuilder().setJobRunId(jobRun.getId()).build();
        stub.killJob(request);
        return true;
    }

    public void killJob(final long jobRunId) {
        JobRunInfo jobRun = jobRunInfoService.getOne(new QueryWrapper<JobRunInfo>()
                .lambda()
                .select(JobRunInfo::getType)
                .eq(JobRunInfo::getId, jobRunId)
                .in(JobRunInfo::getStatus, getNonTerminals()));
        if (jobRun == null) {
            return;
        }

        // exec kill
        JobType type = jobRun.getType();
        jobCommandExecutors.stream()
                .filter(executor -> executor.isSupported(type))
                .findFirst()
                .orElseThrow(() -> new UnrecoverableException("No available job command executor"))
                .kill(jobRunId);

        jobRun = jobRunInfoService.getOne(new QueryWrapper<JobRunInfo>()
                .lambda()
                .select(JobRunInfo::getStatus)
                .eq(JobRunInfo::getId, jobRunId));

        // set status to KILLED
        // Better in a transaction with serializable isolation level.
        if (!jobRun.getStatus().isTerminalState()) {
            JobRunInfo newJobRun = new JobRunInfo();
            newJobRun.setId(jobRunId);
            newJobRun.setStatus(KILLED);
            newJobRun.setStopTime(LocalDateTime.now());
            jobRunInfoService.updateById(newJobRun);
            log.info("Kill job run: {} finished, time: {}", jobRunId, System.currentTimeMillis());
        }
    }
}
