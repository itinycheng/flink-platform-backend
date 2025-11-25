package com.flink.platform.web.service;

import com.flink.platform.common.util.DateUtil;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.enums.Variable;
import com.flink.platform.web.variable.VariableResolver;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.constants.Constant.DOT;
import static com.flink.platform.common.constants.JobConstant.JOB_DIR_FORMAT;
import static com.flink.platform.common.constants.JobConstant.JOB_RUN_DIR;
import static com.flink.platform.common.constants.JobConstant.USER_DIR_FORMAT;
import static com.flink.platform.common.enums.ExecutionStatus.CREATED;
import static com.flink.platform.common.util.DateUtil.DATE_FORMAT;
import static com.flink.platform.common.util.DateUtil.READABLE_TIMESTAMP_FORMAT;

/** addition method. */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class JobRunExtraService {

    private final JobRunInfoService jobRunService;

    private final WorkerApplyService workerApplyService;

    private final StorageService storageService;

    private final List<VariableResolver> variableResolvers;

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
        jobRun.setParams(jobInfo.getParams());
        jobRun.setSubject(jobInfo.getSubject());
        jobRun.setRouteUrl(jobInfo.getRouteUrl());
        jobRun.setHost(host);
        jobRun.setStatus(CREATED);
        return jobRun;
    }

    public Map<String, Object> parseVariables(JobRunInfo jobRun) {
        var variableMap = new LinkedHashMap<String, Object>();
        var content = String.join(", ", jobRun.getSubject(), JsonUtil.toJsonString(jobRun.getConfig()));
        for (var variableResolver : variableResolvers) {
            if (!variableResolver.supports(jobRun, content)) {
                continue;
            }

            var varMap = variableResolver.resolve(jobRun, content);
            // Replace placeholders in the content with actual values
            for (var entry : varMap.entrySet()) {
                content = content.replace(entry.getKey(), String.valueOf(entry.getValue()));
            }
            variableMap.putAll(varMap);
        }

        var params = jobRun.getParams();
        if (MapUtils.isNotEmpty(params)) {
            for (var entry : params.entrySet()) {
                var name = entry.getKey();
                var sqlVar = Variable.matchPrefix(name);
                if (sqlVar != null) {
                    variableMap.put(name, sqlVar.apply(entry.getValue()));
                }
            }
        }

        return variableMap;
    }

    // e.g.: /root/job_run/20250910/user_1/flink_sql/job_328/job_328.20250910153012.sql
    public String buildStoragePath(JobRunInfo jobRun, String fileSuffix) {
        var fileSeparator = storageService.getFileSeparator();
        var relativePath = String.join(
                fileSeparator,
                JOB_RUN_DIR,
                DateUtil.format(jobRun.getCreateTime(), DATE_FORMAT),
                USER_DIR_FORMAT.formatted(jobRun.getUserId()),
                jobRun.getType().name().toLowerCase(),
                JOB_DIR_FORMAT.formatted(jobRun.getJobId()),
                buildTimestampedFileName(jobRun, fileSuffix));
        return String.join(fileSeparator, storageService.getRootPath(), relativePath);
    }

    // buildFileName(jobRun, fileSuffix)
    private String buildTimestampedFileName(JobRunInfo jobRun, String fileSuffix) {
        var readableTime = DateUtil.format(jobRun.getCreateTime(), READABLE_TIMESTAMP_FORMAT);
        return String.join(DOT, jobRun.getJobCode(), readableTime, fileSuffix);
    }
}
