package com.flink.platform.web.variable;

import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.web.service.PluginService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static com.flink.platform.common.constants.JobConstant.APOLLO_CONF_PATTERN;

/**
 * Apollo configuration variable resolver.
 * Resolves ${apollo:namespace:key} placeholders.
 */
@Slf4j
@Order(3)
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ApolloVariableResolver implements VariableResolver {

    private final PluginService pluginService;

    @Override
    public Map<String, Object> resolve(@Nullable JobRunInfo jobRun, String content) {
        try {
            var result = new HashMap<String, Object>();
            var matcher = APOLLO_CONF_PATTERN.matcher(content);
            while (matcher.find()) {
                var paramName = matcher.group();
                var namespace = matcher.group("namespace");
                var key = matcher.group("key");
                if (StringUtils.isNotBlank(namespace) && StringUtils.isNotBlank(key)) {
                    result.put(paramName, pluginService.getApolloConfig(namespace, key));
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to resolve Apollo variables", e);
            throw new RuntimeException("Failed to resolve Apollo variables", e);
        }
    }
}
