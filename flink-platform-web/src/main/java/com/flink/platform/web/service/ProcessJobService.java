package com.flink.platform.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.exception.JobCommandGenException;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.FlinkCommand;
import com.flink.platform.web.command.JobCallback;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.enums.SqlVar;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static java.util.stream.Collectors.toMap;

/** Process job service. */
@Slf4j
@Service
public class ProcessJobService {

    private final JobInfoService jobInfoService;

    private final JobRunInfoService jobRunInfoService;

    private final List<CommandBuilder> jobCommandBuilders;

    private final List<CommandExecutor> jobCommandExecutors;

    @Autowired
    public ProcessJobService(
            JobInfoService jobInfoService,
            JobRunInfoService jobRunInfoService,
            List<CommandBuilder> jobCommandBuilders,
            List<CommandExecutor> jobCommandExecutors) {
        this.jobInfoService = jobInfoService;
        this.jobRunInfoService = jobRunInfoService;
        this.jobCommandBuilders = jobCommandBuilders;
        this.jobCommandExecutors = jobCommandExecutors;
    }

    public JobRunInfo processJob(final long jobId, final Long flowRunId) throws Exception {
        JobCommand jobCommand = null;
        JobInfo jobInfo = null;

        try {
            // step 1: get job info
            jobInfo =
                    jobInfoService.getOne(
                            new QueryWrapper<JobInfo>()
                                    .lambda()
                                    .eq(JobInfo::getId, jobId)
                                    .eq(JobInfo::getStatus, JobStatus.ONLINE));
            if (jobInfo == null) {
                throw new JobCommandGenException(
                        String.format(
                                "The job: %s is no longer exists or in delete status.", jobId));
            }

            // step 2: replace variables in the sql statement
            JobInfo finalJobInfo = jobInfo;
            Map<String, Object> variableMap =
                    Arrays.stream(SqlVar.values())
                            .filter(sqlVar -> sqlVar.type == SqlVar.VarType.VARIABLE)
                            .filter(sqlVar -> finalJobInfo.getSubject().contains(sqlVar.variable))
                            .map(
                                    sqlVar ->
                                            Pair.of(
                                                    sqlVar.variable,
                                                    sqlVar.valueProvider.apply(finalJobInfo)))
                            .collect(toMap(Pair::getLeft, Pair::getRight));
            MapUtils.emptyIfNull(finalJobInfo.getVariables())
                    .forEach(
                            (name, value) -> {
                                SqlVar sqlVar = SqlVar.matchPrefix(name);
                                variableMap.put(name, sqlVar.valueProvider.apply(value));
                            });
            // replace variable with actual value
            for (Map.Entry<String, Object> entry : variableMap.entrySet()) {
                String originSubject = jobInfo.getSubject();
                String distSubject =
                        originSubject.replace(entry.getKey(), entry.getValue().toString());
                jobInfo.setSubject(distSubject);
            }

            JobType jobType = jobInfo.getType();
            String version = jobInfo.getVersion();

            // step 3: build job command, create a SqlContext if needed
            jobCommand =
                    jobCommandBuilders.stream()
                            .filter(builder -> builder.isSupported(jobType, version))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new JobCommandGenException(
                                                    "No available job command builder"))
                            .buildCommand(flowRunId, jobInfo);

            // step 4: submit job
            LocalDateTime submitTime = LocalDateTime.now();
            final JobCommand command = jobCommand;
            JobCallback callback =
                    jobCommandExecutors.stream()
                            .filter(executor -> executor.isSupported(command))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new JobCommandGenException(
                                                    "No available job command executor"))
                            .execCommand(command);

            // step 5: write job run info to db
            ExecutionStatus executionStatus = getExecutionStatus(jobType, callback);
            JobRunInfo jobRunInfo = new JobRunInfo();
            jobRunInfo.setName(jobInfo.getName() + "-" + System.currentTimeMillis());
            jobRunInfo.setJobId(jobInfo.getId());
            jobRunInfo.setFlowRunId(flowRunId);
            jobRunInfo.setUserId(jobInfo.getUserId());
            jobRunInfo.setType(jobInfo.getType());
            jobRunInfo.setVersion(jobInfo.getVersion());
            jobRunInfo.setDeployMode(jobInfo.getDeployMode());
            jobRunInfo.setExecMode(jobInfo.getExecMode());
            jobRunInfo.setRouteUrl(jobInfo.getRouteUrl());
            jobRunInfo.setConfig(jobInfo.getConfig());
            jobRunInfo.setSubject(jobInfo.getSubject());
            jobRunInfo.setStatus(executionStatus);
            jobRunInfo.setVariables(variableMap);
            jobRunInfo.setBackInfo(JsonUtil.toJsonString(callback));
            jobRunInfo.setSubmitTime(submitTime);
            if (executionStatus.isTerminalState()) {
                jobRunInfo.setStopTime(LocalDateTime.now());
            }
            jobRunInfoService.save(jobRunInfo);

            // step 6: print job command info
            log.info("Job: {} submitted, time: {}", jobId, System.currentTimeMillis());

            return jobRunInfo;
        } finally {
            if (jobInfo != null && jobInfo.getType() == JobType.FLINK_SQL && jobCommand != null) {
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

    private ExecutionStatus getExecutionStatus(JobType jobType, JobCallback callback) {
        if (callback.getStatus() != SUCCESS) {
            return callback.getStatus();
        }

        switch (jobType) {
            case CLICKHOUSE_SQL:
            case COMMON_JAR:
            case CONDITION:
                return SUCCESS;
            default:
                return ExecutionStatus.SUBMITTED;
        }
    }
}
