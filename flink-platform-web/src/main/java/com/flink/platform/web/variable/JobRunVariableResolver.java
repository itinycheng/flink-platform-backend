package com.flink.platform.web.variable;

import com.flink.platform.dao.entity.JobRunInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static com.flink.platform.common.constants.JobConstant.JOB_RUN_PATTERN;

/**
 * Job run variable resolver.
 * Resolves ${jobRun:field} placeholders.
 */
@Slf4j
@Order(1)
@Component
public class JobRunVariableResolver implements VariableResolver {

    @Override
    public Map<String, Object> resolve(JobRunInfo jobRun, String content) {
        var result = new HashMap<String, Object>();
        var matcher = JOB_RUN_PATTERN.matcher(content);
        while (matcher.find()) {
            var field = matcher.group("field");
            Object value = null;
            if ("code".equalsIgnoreCase(field)) {
                value = jobRun.getJobCode();
            } else if ("id".equalsIgnoreCase(field)) {
                value = jobRun.getId();
            }
            result.put(matcher.group(), value);
        }
        return result;
    }
}
