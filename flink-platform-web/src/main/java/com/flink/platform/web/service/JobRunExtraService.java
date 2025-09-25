package com.flink.platform.web.service;

import com.flink.platform.common.util.DateUtil;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.enums.Placeholder;
import com.flink.platform.web.enums.Variable;
import com.flink.platform.web.util.PathUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.flink.platform.common.constants.Constant.DOT;
import static com.flink.platform.common.constants.JobConstant.JSON_FILE_SUFFIX;
import static com.flink.platform.common.enums.ExecutionStatus.CREATED;
import static com.flink.platform.common.util.DateUtil.DATE_FORMAT;
import static com.flink.platform.common.util.DateUtil.READABLE_TIMESTAMP_FORMAT;
import static com.flink.platform.web.util.PathUtil.JOB_DIR_FORMAT;
import static com.flink.platform.web.util.PathUtil.USER_DIR_FORMAT;

/** addition method. */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class JobRunExtraService {

    private final JobRunInfoService jobRunService;

    private final WorkerApplyService workerApplyService;

    private final StorageService storageService;

    public Long createJobRun(JobInfo jobInfo, Long flowRunId) {
        var worker = workerApplyService.randomWorker(jobInfo.getRouteUrl());
        if (worker == null || StringUtils.isEmpty(worker.getIp())) {
            throw new IllegalStateException("No available worker found for job: " + jobInfo.getName());
        }

        var jobRun = createFrom(jobInfo, flowRunId, worker.getIp());
        jobRunService.save(jobRun);
        return jobRun.getId();
    }

    public JobRunInfo createFrom(JobInfo jobInfo, Long flowRunId, String host) {
        var jobRun = new JobRunInfo();
        jobRun.setName(jobInfo.getName() + "-" + System.currentTimeMillis());
        jobRun.setJobId(jobInfo.getId());
        jobRun.setFlowRunId(flowRunId);
        jobRun.setUserId(jobInfo.getUserId());
        jobRun.setType(jobInfo.getType());
        jobRun.setVersion(jobInfo.getVersion());
        jobRun.setDeployMode(jobInfo.getDeployMode());
        jobRun.setExecMode(jobInfo.getExecMode());
        jobRun.setConfig(jobInfo.getConfig());
        jobRun.setVariables(jobInfo.getVariables());
        jobRun.setSubject(jobInfo.getSubject());
        jobRun.setRouteUrl(jobInfo.getRouteUrl());
        jobRun.setHost(host);
        jobRun.setStatus(CREATED);
        return jobRun;
    }

    public Map<String, Object> parseVariables(JobRunInfo jobRun) {
        var variableMap = new LinkedHashMap<String, Object>();
        var content = String.join(", ", jobRun.getSubject(), JsonUtil.toJsonString(jobRun.getConfig()));
        for (Placeholder placeholder : Placeholder.values()) {
            if (!content.contains(placeholder.wildcard)) {
                continue;
            }

            var varMap = placeholder.apply(jobRun, content);
            // Replace placeholders in the content with actual values
            for (var entry : varMap.entrySet()) {
                content = content.replace(entry.getKey(), entry.getValue().toString());
            }
            variableMap.putAll(varMap);
        }

        var variables = jobRun.getVariables();
        if (MapUtils.isNotEmpty(variables)) {
            for (var entry : variables.entrySet()) {
                var name = entry.getKey();
                var sqlVar = Variable.matchPrefix(name);
                if (sqlVar != null) {
                    variableMap.put(name, sqlVar.apply(entry.getValue()));
                }
            }
        }

        return variableMap;
    }

    // e.g.: job_run/20250910/user_1/flink_sql/job_328
    public String buildStorageFilePath(JobRunInfo jobRun) {
        String fileSeparator = storageService.getFileSeparator();
        var relativePath = String.join(
                fileSeparator,
                PathUtil.JOB_ROOT_DIR,
                DateUtil.format(jobRun.getCreateTime(), DATE_FORMAT),
                String.format(USER_DIR_FORMAT, jobRun.getUserId()),
                jobRun.getType().name().toLowerCase(),
                String.format(JOB_DIR_FORMAT, jobRun.getJobId()),
                buildJsonFileName(jobRun));
        return String.join(fileSeparator, storageService.getRootPath(), relativePath);
    }

    public String buildJsonFileName(JobRunInfo jobRun) {
        var readableTime = DateUtil.format(jobRun.getCreateTime(), READABLE_TIMESTAMP_FORMAT);
        return String.join(DOT, jobRun.getJobCode(), readableTime, JSON_FILE_SUFFIX);
    }
}
