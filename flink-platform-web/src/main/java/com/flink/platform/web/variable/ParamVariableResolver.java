package com.flink.platform.web.variable;

import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobParamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.flink.platform.common.constants.JobConstant.PARAM_PATTERN;

/**
 * Job parameter variable resolver.
 * Resolves ${param:paramName} placeholders.
 */
@Slf4j
@Order(4)
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ParamVariableResolver implements VariableResolver {

    private final JobParamService jobParamService;

    @Override
    public boolean supports(JobRunInfo jobRun, String content) {
        return PARAM_PATTERN.matcher(content).find();
    }

    @Override
    public Map<String, Object> resolve(JobRunInfo jobRun, String content) {
        var jobParams = jobParamService.getJobParams(jobRun.getJobId());
        if (CollectionUtils.isEmpty(jobParams)) {
            return Collections.emptyMap();
        }

        var result = new HashMap<String, Object>();
        var matcher = PARAM_PATTERN.matcher(content);
        while (matcher.find()) {
            var variable = matcher.group();
            var paramName = matcher.group("name");
            jobParams.stream()
                    .filter(jobParam -> jobParam.getParamName().equalsIgnoreCase(paramName))
                    .findFirst()
                    .ifPresent(jobParam -> result.put(variable, jobParam.getParamValue()));
        }
        return result;
    }
}
