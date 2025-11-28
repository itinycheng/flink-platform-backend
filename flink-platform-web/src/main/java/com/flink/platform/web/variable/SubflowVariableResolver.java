package com.flink.platform.web.variable;

import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.FlowJob;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.flink.platform.common.constants.JobConstant.SUBFLOW_PATTERN;

/**
 * Subflow variable resolver. ${subflow:paramName} ${subflow:code:paramName}
 */
@Slf4j
@Order(6)
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class SubflowVariableResolver implements VariableResolver {

    private final JobRunInfoService jobRunService;

    private final JobFlowRunService jobFlowRunService;

    @Override
    public Map<String, Object> resolve(JobRunInfo jobRun, String content) {
        var subflowParams = collectSubflowParams(jobRun.getFlowRunId());
        var result = new HashMap<String, Object>();
        var matcher = SUBFLOW_PATTERN.matcher(content);
        while (matcher.find()) {
            var variable = matcher.group();
            var key = matcher.group("name");
            result.put(variable, subflowParams.get(key));
        }
        return result;
    }

    private Map<String, Object> collectSubflowParams(Long flowRunId) {
        var subflowJobRuns = jobRunService.findJobsOfSubflowType(flowRunId);
        var mergedParams = new HashMap<String, Object>();
        for (JobRunInfo subflowJobRun : subflowJobRuns) {
            var config = subflowJobRun.getConfig().unwrap(FlowJob.class);
            if (config == null) {
                continue;
            }

            var subflowParams = getParamsOfFlowRun(subflowJobRun);
            if (MapUtils.isEmpty(subflowParams)) {
                continue;
            }

            switch (config.getInheritParamMode()) {
                case ALLOW -> subflowParams.forEach((k, v) -> mergeParamIntoMap(mergedParams, k, v));
                case CUSTOM -> {
                    var inheritableParamNames = config.getParamNames();
                    if (inheritableParamNames == null || inheritableParamNames.isEmpty()) {
                        break;
                    }

                    subflowParams.forEach((k, v) -> {
                        if (inheritableParamNames.contains(k)) {
                            mergeParamIntoMap(mergedParams, k, v);
                        }
                    });
                }
                case null, default -> {}
            }
        }
        return mergedParams;
    }

    @SuppressWarnings("unchecked")
    private void mergeParamIntoMap(Map<String, Object> targetMap, String key, Object value) {
        if (key == null || value == null) {
            return;
        }

        var existed = targetMap.get(key);
        if (existed == null) {
            targetMap.put(key, value);
            return;
        }

        if (existed instanceof Collection<?> collection) {
            ((Collection<Object>) collection).add(value);
        } else {
            var list = new ArrayList<>();
            list.add(existed);
            list.add(value);
            targetMap.put(key, list);
        }
    }

    private Map<String, Object> getParamsOfFlowRun(JobRunInfo subflowJobRun) {
        if (subflowJobRun == null) {
            return Map.of();
        }

        var callback = subflowJobRun.getBackInfo();
        if (callback == null || callback.getFlowRunId() == null) {
            return Map.of();
        }

        var jobFlowRun = jobFlowRunService.getById(callback.getFlowRunId());
        return jobFlowRun != null ? jobFlowRun.getParams() : Map.of();
    }
}
