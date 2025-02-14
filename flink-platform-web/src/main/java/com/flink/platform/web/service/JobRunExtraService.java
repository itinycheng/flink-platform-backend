package com.flink.platform.web.service;

import com.flink.platform.common.constants.Constant;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.enums.Placeholder;
import com.flink.platform.web.enums.Variable;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static com.flink.platform.common.enums.ExecutionStatus.CREATED;

/** addition method. */
@Service
public class JobRunExtraService {

    @Autowired
    private JobRunInfoService jobRunService;

    @Autowired
    private WorkerApplyService workerApplyService;

    public Long createJobRun(JobInfo jobInfo, Long flowRunId) {
        var worker = workerApplyService.randomWorker(jobInfo.getRouteUrl());
        var host = worker != null ? worker.getIp() : Constant.HOST_IP;
        var jobRun = createFrom(jobInfo, flowRunId, host);
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

    public Pair<String, Map<String, Object>> parseVarsAndContent(JobRunInfo jobRun) {
        var variableMap = new HashMap<String, Object>();

        for (Placeholder placeholder : Placeholder.values()) {
            var subject = jobRun.getSubject();
            if (!subject.contains(placeholder.wildcard)) {
                continue;
            }

            var vars = placeholder.provider.apply(jobRun);
            variableMap.putAll(vars);

            for (var entry : vars.entrySet()) {
                subject = subject.replace(entry.getKey(), entry.getValue().toString());
            }

            jobRun.setSubject(subject);
        }

        var content = jobRun.getSubject();
        var variables = jobRun.getVariables();
        if (MapUtils.isNotEmpty(variables)) {
            for (var entry : variables.entrySet()) {
                var name = entry.getKey();
                var sqlVar = Variable.matchPrefix(name);
                if (sqlVar != null) {
                    var value = sqlVar.provider.apply(entry.getValue());
                    variableMap.put(name, value);
                    content = content.replace(name, value.toString());
                }
            }
        }

        return Pair.of(content, variableMap);
    }
}
