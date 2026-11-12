package com.flink.platform.web.service;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.exception.UnrecoverableException;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.BaseJob;
import com.flink.platform.dao.entity.task.FlinkJob;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.variable.SetParamVariableResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static com.flink.platform.common.constants.Constant.HOST_IP;
import static com.flink.platform.common.enums.ExecutionStatus.CREATED;
import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

/** Process job service. */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ProcessJobService {

    private static final List<ExecutionStatus> EXPECTED_CREATED = List.of(CREATED);

    private final JobRunInfoService jobRunInfoService;

    private final JobRunExtraService jobRunExtraService;

    private final JobFlowRunService jobFlowRunService;

    private final SetParamVariableResolver setParamResolver;

    private final List<CommandBuilder> jobCommandBuilders;

    private final List<CommandExecutor> jobCommandExecutors;

    public void processJob(final long jobRunId) throws Exception {
        var jobRun = jobRunInfoService.getById(jobRunId);
        if (jobRun == null) {
            throw new UnrecoverableException("The job run: %s is no longer exists.".formatted(jobRunId));
        }

        if (jobRun.getStatus() != CREATED) {
            log.info("Job run {} is already submitted or terminated; skipping duplicate process request", jobRunId);
            return;
        }

        var varMap = jobRunExtraService.parseVariables(jobRun);
        var replacedContent = replaceVariables(jobRun.getSubject(), varMap);
        var replacedConfig = replaceVariables(jobRun.getConfig(), varMap);
        if (replacedConfig == null) {
            replacedConfig = jobRun.getConfig();
        }

        // create submit time.
        var submitTime = LocalDateTime.now();

        var newJobRun = new JobRunInfo();
        newJobRun.setId(jobRun.getId());
        newJobRun.setParams(varMap);
        newJobRun.setSubject(replacedContent);
        newJobRun.setConfig(replacedConfig);
        newJobRun.setHost(HOST_IP);
        newJobRun.setStatus(SUBMITTED);
        newJobRun.setSubmitTime(submitTime);

        jobRun.setParams(varMap);
        jobRun.setSubject(replacedContent);
        jobRun.setConfig(replacedConfig);
        jobRun.setSubmitTime(submitTime);

        var builder = getBuilderOrThrow(jobRun);
        var executor = getExecutorOrThrow(jobRun);
        var command = builder.buildCommand(jobRun);
        if (jobRunInfoService.updateStatusFromAnyTo(jobRunId, EXPECTED_CREATED, newJobRun) != 1) {
            log.warn("Job run {} is no longer launchable; skipping duplicate submission", jobRunId);
            return;
        }

        updateParamsInJobFlowRun(jobRun);
        var callback = executor.exec(command);

        var executionStatus = callback.getStatus();
        var result = new JobRunInfo();
        result.setId(jobRunId);
        result.setStatus(executionStatus);
        result.setTrimmedBackInfo(callback);
        if (executionStatus.isTerminalState()) {
            result.setEndTime(LocalDateTime.now());
        }

        var executionStatuses = expectedForCallback(executionStatus);
        if (jobRunInfoService.updateStatusFromAnyTo(jobRunId, executionStatuses, result) != 1) {
            log.warn("Job run {} callback did not change its committed status", jobRunId);
        }

        log.info("Job run: {} submitted, time: {}", jobRunId, System.currentTimeMillis());
    }

    private static List<ExecutionStatus> expectedForCallback(ExecutionStatus executionStatus) {
        return switch (executionStatus) {
            case SUBMITTED -> List.of(SUBMITTED);
            case WAITING -> List.of(SUBMITTED, WAITING);
            case CREATED, KILLING ->
                throw new IllegalArgumentException("Unexpected job submission result: " + executionStatus);
            default -> List.of(SUBMITTED, WAITING, RUNNING);
        };
    }

    private CommandBuilder getBuilderOrThrow(JobRunInfo jobRun) {
        return jobCommandBuilders.stream()
                .filter(builder -> builder.isSupported(jobRun.getType(), jobRun.getVersion()))
                .findFirst()
                .orElseThrow(() -> new UnrecoverableException("No available job command builder"));
    }

    private CommandExecutor getExecutorOrThrow(JobRunInfo jobRun) {
        return jobCommandExecutors.stream()
                .filter(candidate -> candidate.isSupported(jobRun.getType()))
                .findFirst()
                .orElseThrow(() -> new UnrecoverableException("No available job command executor"));
    }

    private BaseJob replaceVariables(BaseJob baseJob, Map<String, Object> varValueMap) {
        // TODO: only support flink job for now.
        if (!(baseJob instanceof FlinkJob)) {
            return baseJob;
        }

        var jsonString = JsonUtil.toJsonString(baseJob);
        var replaced = replaceVariables(jsonString, varValueMap);
        return JsonUtil.toBean(replaced, BaseJob.class);
    }

    private String replaceVariables(String content, Map<String, Object> varValueMap) {
        if (StringUtils.isEmpty(content)) {
            return content;
        }

        for (var entry : varValueMap.entrySet()) {
            content = content.replace(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return content;
    }

    // public for testing
    public void updateParamsInJobFlowRun(JobRunInfo jobRun) {
        var params = jobRun.getParams();
        if (MapUtils.isEmpty(params)) {
            return;
        }

        // option: use ListUtil::mergeToList if you need to merge values into list.
        params = params.entrySet().stream()
                .map(entry -> Pair.of(setParamResolver.getSetParamKey(entry.getKey()), entry.getValue()))
                .filter(pair -> isNotEmpty(pair.getKey()))
                .collect(toMap(Pair::getKey, Entry::getValue));
        if (MapUtils.isEmpty(params)) {
            return;
        }

        jobFlowRunService.lockAndUpdateParams(jobRun.getFlowRunId(), params);
    }
}
