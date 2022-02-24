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

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.web.entity.JobQuartzInfo.JOB_RUN_ID;
import static java.util.stream.Collectors.toMap;

/** Process job service. */
@Slf4j
@Service
public class ProcessJobService {

    private final JobInfoService jobInfoService;

    private final JobRunInfoService jobRunInfoService;

    private final JobFlowScheduleService jobFlowScheduleService;

    private final List<CommandBuilder> jobCommandBuilders;

    private final List<CommandExecutor> jobCommandExecutors;

    @Autowired
    public ProcessJobService(
            JobInfoService jobInfoService,
            JobRunInfoService jobRunInfoService,
            JobFlowScheduleService jobFlowScheduleService,
            List<CommandBuilder> jobCommandBuilders,
            List<CommandExecutor> jobCommandExecutors) {
        this.jobInfoService = jobInfoService;
        this.jobRunInfoService = jobRunInfoService;
        this.jobFlowScheduleService = jobFlowScheduleService;
        this.jobCommandBuilders = jobCommandBuilders;
        this.jobCommandExecutors = jobCommandExecutors;
    }

    public Long processJob(final String jobCode, final Map<String, Object> dataMap)
            throws Exception {
        JobCommand jobCommand = null;
        JobInfo jobInfo = null;
        Long jobRunId = null;

        try {
            if (dataMap != null && dataMap.get(JOB_RUN_ID) != null) {
                jobRunId = ((Number) dataMap.get(JOB_RUN_ID)).longValue();
            }

            // step 1: get job info
            jobInfo =
                    jobInfoService.getOne(
                            new QueryWrapper<JobInfo>()
                                    .lambda()
                                    .eq(JobInfo::getCode, jobCode)
                                    .ne(JobInfo::getStatus, JobStatus.OFFLINE.getCode()));
            if (jobInfo == null) {
                throw new JobCommandGenException(
                        String.format(
                                "The job: %s is no longer exists or in delete status.", jobCode));
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
                            .buildCommand(jobInfo);

            // step 4: submit job
            String commandString = jobCommand.toCommandString();
            JobCallback callback =
                    jobCommandExecutors.stream()
                            .filter(executor -> executor.isSupported(jobType))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new JobCommandGenException(
                                                    "No available job command executor"))
                            .execCommand(commandString);

            // step 5: write msg back to db
            if (jobRunId != null) {
                JobRunInfo jobRunInfo = new JobRunInfo();
                jobRunInfo.setId(jobRunId);
                jobRunInfo.setCommand(commandString);
                jobRunInfo.setStatus(getExecutionStatus(jobType, callback).getCode());
                jobRunInfo.setVariables(JsonUtil.toJsonString(variableMap));
                jobRunInfo.setBackInfo(JsonUtil.toJsonString(callback));
                jobRunInfo.setSubmitTime(LocalDateTime.now());
                jobRunInfoService.updateById(jobRunInfo);
            }

            // step 6: print job command info
            log.info("Job: {} submitted, time: {}", jobCode, System.currentTimeMillis());
        } catch (Exception exception) {
            handleFailure(jobRunId);
            throw exception;
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

        return jobRunId;
    }

    public void handleFailure(Long jobRunId) {
        if (jobRunId == null) {
            return;
        }

        JobRunInfo jobRunInfo = new JobRunInfo();
        jobRunInfo.setId(jobRunId);
        jobRunInfo.setStatus(FAILURE.getCode());
        jobRunInfoService.updateById(jobRunInfo);

        jobRunInfo = jobRunInfoService.getById(jobRunId);
        if (jobRunInfo.getFlowRunId() != null) {
            jobFlowScheduleService.terminateFlow(jobRunInfo.getFlowRunId(), FAILURE);
        }
    }

    private ExecutionStatus getExecutionStatus(JobType jobType, JobCallback callback) {
        if (callback.getStatus() != SUCCESS) {
            return callback.getStatus();
        }

        switch (jobType) {
            case CLICKHOUSE_SQL:
            case COMMON_JAR:
                return SUCCESS;
            default:
                return ExecutionStatus.SUBMITTED;
        }
    }
}
