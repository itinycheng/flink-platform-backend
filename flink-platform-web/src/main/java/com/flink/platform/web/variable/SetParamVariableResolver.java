package com.flink.platform.web.variable;

import com.flink.platform.dao.entity.JobRunInfo;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static com.flink.platform.common.constants.Constant.EMPTY;
import static com.flink.platform.common.constants.JobConstant.SET_PARAM_PATTERN;

/**
 * Set value variable resolver.
 * Resolves ${setParam:key=value} placeholders.
 */
@Slf4j
@Order(99)
@Component
public class SetParamVariableResolver implements VariableResolver {

    @Override
    public Map<String, Object> resolve(@Nullable JobRunInfo jobRun, String content) {
        var result = new HashMap<String, Object>();
        var matcher = SET_PARAM_PATTERN.matcher(content);
        while (matcher.find()) {
            var variable = matcher.group();
            var value = matcher.group("value");
            result.put(variable, value);
        }
        return result;
    }

    public String getSetParamKey(String value) {
        var matcher = SET_PARAM_PATTERN.matcher(value);
        if (matcher.find()) {
            return matcher.group("key");
        }
        return EMPTY;
    }
}
