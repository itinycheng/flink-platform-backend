package com.flink.platform.web.service;

import com.flink.platform.common.exception.UnrecoverableException;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.BaseJob;
import com.flink.platform.dao.entity.task.FlinkJob;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.CommandExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.constants.Constant.HOST_IP;

/** Process job service. */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ProcessJobService {

    private final JobRunInfoService jobRunInfoService;

    private final JobRunExtraService jobRunExtraService;

    private final List<CommandBuilder> jobCommandBuilders;

    private final List<CommandExecutor> jobCommandExecutors;

    public void processJob(final long jobRunId) throws Exception {
        // step 1: get job info
        var jobRunInfo = jobRunInfoService.getById(jobRunId);
        if (jobRunInfo == null) {
            throw new UnrecoverableException("The job run: %s is no longer exists.".formatted(jobRunId));
        }

        // step 2: Update jobRun and prepare environment before execution.
        var varMap = jobRunExtraService.parseVariables(jobRunInfo);
        var replacedContent = replaceVariables(jobRunInfo.getSubject(), varMap);
        var replacedConfig = replaceVariables(jobRunInfo.getConfig(), varMap);
        if (replacedConfig == null) {
            replacedConfig = jobRunInfo.getConfig();
        }

        // create submit time.
        var submitTime = LocalDateTime.now();

        jobRunInfo.setVariables(varMap);
        jobRunInfo.setSubject(replacedContent);
        jobRunInfo.setConfig(replacedConfig);
        jobRunInfo.setSubmitTime(submitTime);

        var newJobRun = new JobRunInfo();
        newJobRun.setId(jobRunInfo.getId());
        newJobRun.setVariables(varMap);
        newJobRun.setSubject(replacedContent);
        newJobRun.setConfig(replacedConfig);
        newJobRun.setHost(HOST_IP);
        newJobRun.setSubmitTime(submitTime);
        jobRunInfoService.updateById(newJobRun);

        var jobType = jobRunInfo.getType();
        var version = jobRunInfo.getVersion();

        // step 3: build job command, create a SqlContext if needed
        var command = jobCommandBuilders.stream()
                .filter(builder -> builder.isSupported(jobType, version))
                .findFirst()
                .orElseThrow(() -> new UnrecoverableException("No available job command builder"))
                .buildCommand(jobRunInfo.getFlowRunId(), jobRunInfo);

        // step 4: submit job
        var callback = jobCommandExecutors.stream()
                .filter(executor -> executor.isSupported(jobType))
                .findFirst()
                .orElseThrow(() -> new UnrecoverableException("No available job command executor"))
                .exec(command);

        // step 5: write job run info to db
        var executionStatus = callback.getStatus();
        newJobRun = new JobRunInfo();
        newJobRun.setId(jobRunInfo.getId());
        newJobRun.setStatus(executionStatus);
        newJobRun.setBackInfo(callback);
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
            content = content.replace(entry.getKey(), entry.getValue().toString());
        }
        return content;
    }
}
