package com.flink.platform.web.variable;

import com.flink.platform.dao.entity.JobRunInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.CaseUtils;
import org.springframework.beans.BeanWrapperImpl;
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
        var wrapper = new BeanWrapperImpl(jobRun);
        while (matcher.find()) {
            var field = matcher.group("field");
            Object value = null;
            try {
                if (wrapper.isReadableProperty(field)) {
                    value = wrapper.getPropertyValue(field);
                }

                field = CaseUtils.toCamelCase(field, false, '_', '-', '.');
                if (wrapper.isReadableProperty(field)) {
                    value = wrapper.getPropertyValue(field);
                }
            } catch (Exception e) {
                log.info("Failed to get jobRun field: {}", field, e);
            }
            result.put(matcher.group(), value);
        }
        return result;
    }
}
