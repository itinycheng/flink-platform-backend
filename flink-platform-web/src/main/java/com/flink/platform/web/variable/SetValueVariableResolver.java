package com.flink.platform.web.variable;

import com.flink.platform.dao.entity.JobRunInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static com.flink.platform.common.constants.JobConstant.SET_VALUE_PATTERN;

/**
 * Set value variable resolver.
 * Resolves ${setValue:key=value} placeholders.
 */
@Slf4j
@Order(99)
@Component
public class SetValueVariableResolver implements VariableResolver {

    @Override
    public Map<String, Object> resolve(JobRunInfo jobRun, String content) {
        var result = new HashMap<String, Object>();
        var matcher = SET_VALUE_PATTERN.matcher(content);
        while (matcher.find()) {
            var variable = matcher.group();
            var value = matcher.group("value");
            result.put(variable, value);
        }
        return result;
    }

    public String getSetValueKey(String value) {
        var matcher = SET_VALUE_PATTERN.matcher(value);
        if (matcher.find()) {
            return matcher.group("key");
        }
        return null;
    }
}
