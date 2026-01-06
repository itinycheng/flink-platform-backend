package com.flink.platform.web.service;

import com.flink.platform.common.exception.UnrecoverableException;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.BaseJob;
import com.flink.platform.dao.entity.task.FlinkJob;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.variable.SetValueVariableResolver;
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
import static java.util.stream.Collectors.toMap;

/** Process job service. */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ProcessJobService {

    private final JobRunInfoService jobRunInfoService;

    private final JobRunExtraService jobRunExtraService;

    private final JobFlowRunService jobFlowRunService;

    private final SetValueVariableResolver setValueResolver;

    private final List<CommandBuilder> jobCommandBuilders;

    private final List<CommandExecutor> jobCommandExecutors;

    public void processJob(final long jobRunId) throws Exception {
        // step 1: get job info
        var jobRun = jobRunInfoService.getById(jobRunId);
        if (jobRun == null) {
            throw new UnrecoverableException("The job run: %s is no longer exists.".formatted(jobRunId));
        }

        // step 2: Update jobRun and prepare environment before execution.
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
        newJobRun.setSubmitTime(submitTime);
        jobRunInfoService.updateById(newJobRun);

        jobRun.setParams(varMap);
        jobRun.setSubject(replacedContent);
        jobRun.setConfig(replacedConfig);
        jobRun.setSubmitTime(submitTime);
        updateParamsInJobFlowRun(jobRun);

        var jobType = jobRun.getType();
        var version = jobRun.getVersion();

        // step 3: build job command, create a SqlContext if needed
        var command = jobCommandBuilders.stream()
                .filter(builder -> builder.isSupported(jobType, version))
                .findFirst()
                .orElseThrow(() -> new UnrecoverableException("No available job command builder"))
                .buildCommand(jobRun.getFlowRunId(), jobRun);

        // step 4: submit job
        var callback = jobCommandExecutors.stream()
                .filter(executor -> executor.isSupported(jobType))
                .findFirst()
                .orElseThrow(() -> new UnrecoverableException("No available job command executor"))
                .exec(command);

        // step 5: write job run info to db
        var executionStatus = callback.getStatus();
        newJobRun = new JobRunInfo();
        newJobRun.setId(jobRun.getId());
        newJobRun.setStatus(executionStatus);
        newJobRun.setTrimmedBackInfo(callback);
        if (executionStatus.isTerminalState()) {
            newJobRun.setEndTime(LocalDateTime.now());
        }
        jobRunInfoService.updateById(newJobRun);

        // step 6: print job command info
        log.info("Job run: {} submitted, time: {}", jobRunId, System.currentTimeMillis());
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
                .map(entry -> Pair.of(setValueResolver.getSetValueKey(entry.getKey()), entry.getValue()))
                .filter(pair -> pair.getKey() != null)
                .collect(toMap(Pair::getKey, Entry::getValue));
        if (MapUtils.isEmpty(params)) {
            return;
        }

        jobFlowRunService.lockAndUpdateParams(jobRun.getFlowRunId(), params);
    }
}
