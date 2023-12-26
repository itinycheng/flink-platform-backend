package com.flink.platform.web.service;

import com.flink.platform.common.constants.Constant;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.enums.Placeholder;
import com.flink.platform.web.enums.Variable;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
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
        Worker worker = workerApplyService.randomWorker(jobInfo.getRouteUrl());
        String host = worker != null ? worker.getIp() : Constant.HOST_IP;
        return _parseVarsAndSave(jobInfo, flowRunId, host);
    }

    @Transactional
    public Long _parseVarsAndSave(JobInfo jobInfo, Long flowRunId, String host) {
        // Save jobRun, generate an id.
        JobRunInfo jobRun = createFrom(jobInfo, flowRunId, host);
        jobRunService.save(jobRun);
        // Update variables/subject in jobRun.
        Map<String, Object> variableMap = parseVariables(jobRun);
        String plainSubject = getPlainSubject(jobRun.getSubject(), variableMap);
        JobRunInfo newJobRun = new JobRunInfo();
        newJobRun.setId(jobRun.getId());
        newJobRun.setVariables(variableMap);
        newJobRun.setSubject(plainSubject);
        jobRunService.updateById(newJobRun);
        return jobRun.getId();
    }

    public JobRunInfo createFrom(JobInfo jobInfo, Long flowRunId, String host) {
        JobRunInfo jobRun = new JobRunInfo();
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

    private Map<String, Object> parseVariables(JobRunInfo jobRun) {
        Map<String, Object> variableMap = new HashMap<>();
        Arrays.stream(Placeholder.values())
                .filter(placeholder -> jobRun.getSubject().contains(placeholder.wildcard))
                .map(placeholder -> placeholder.provider.apply(jobRun))
                .forEach(variableMap::putAll);

        MapUtils.emptyIfNull(jobRun.getVariables()).forEach((name, value) -> {
            Variable sqlVar = Variable.matchPrefix(name);
            variableMap.put(name, sqlVar.provider.apply(value));
        });
        return variableMap;
    }

    public static String getPlainSubject(String subject, Map<String, Object> variableMap) {
        for (Map.Entry<String, Object> entry : variableMap.entrySet()) {
            subject = subject.replace(entry.getKey(), entry.getValue().toString());
        }
        return subject;
    }
}
