package com.flink.platform.web.variable;

import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobParamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static com.flink.platform.common.constants.JobConstant.PARAM_PATTERN;

/**
 * Job parameter variable resolver. Resolves ${param:paramName} placeholders.
 */
@Slf4j
@Order(4)
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ParamVariableResolver implements VariableResolver {

    private final JobParamService jobParamService;

    private final JobFlowRunService jobFlowRunService;

    private final SubflowVariableResolver subflowResolver;

    @Override
    public boolean supports(JobRunInfo jobRun, String content) {
        return jobRun != null && PARAM_PATTERN.matcher(content).find();
    }

    @Override
    public Map<String, Object> resolve(JobRunInfo jobRun, String content) {
        // priority: global < sub flow < job flow < job
        var paramMap = new HashMap<String, Object>();
        var globalParams = jobParamService.getJobParams(jobRun.getJobId());
        if (CollectionUtils.isNotEmpty(globalParams)) {
            globalParams.forEach(globalParam -> paramMap.put(globalParam.getParamName(), globalParam.getParamValue()));
        }

        var subflowParamMap = subflowResolver.collectSubflowParams(jobRun.getFlowRunId());
        if (MapUtils.isNotEmpty(subflowParamMap)) {
            paramMap.putAll(subflowParamMap);
        }

        var jobFlowRun = jobFlowRunService.getById(jobRun.getFlowRunId());
        var flowParamMap = jobFlowRun != null ? jobFlowRun.getParams() : null;
        if (MapUtils.isNotEmpty(flowParamMap)) {
            paramMap.putAll(flowParamMap);
        }

        var jobParamMap = jobRun.getParams();
        if (MapUtils.isNotEmpty(jobParamMap)) {
            paramMap.putAll(jobParamMap);
        }

        var result = new HashMap<String, Object>();
        var matcher = PARAM_PATTERN.matcher(content);
        while (matcher.find()) {
            var variable = matcher.group();
            var paramName = matcher.group("name");
            var paramValue = paramMap.get(paramName);
            result.put(variable, paramValue);
        }
        return result;
    }
}
