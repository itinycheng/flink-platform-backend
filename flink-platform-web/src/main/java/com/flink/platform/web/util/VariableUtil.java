package com.flink.platform.web.util;

import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.variable.ApolloVariableResolver;
import lombok.extern.slf4j.Slf4j;

/**
 * Apollo utils.
 */
@Slf4j
public class VariableUtil {

    public static String apolloConfig(String param) {
        var resolver = SpringContext.getBean(ApolloVariableResolver.class);
        var result = resolver.resolve(null, param);
        if (!result.isEmpty()) {
            var value = result.values().iterator().next();
            if (value != null) {
                log.debug("Apollo config found, param:{}, value:{}", param, value);
                return value.toString();
            }
        }
        return param;
    }
}
