package com.flink.platform.web.util;

import com.flink.platform.web.enums.Placeholder;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Apollo utils.
 */
@Slf4j
public class PlaceholderUtil {

    public static String apolloConfig(String param) {
        if (!param.startsWith(Placeholder.APOLLO.wildcard)) {
            return param;
        }

        Map<String, Object> result = Placeholder.APOLLO.provider.apply(param);
        if (result != null && !result.isEmpty()) {
            Object value = result.values().iterator().next();
            if (value != null) {
                log.debug("Apollo config found, param:{}, value:{}", param, value);
                return value.toString();
            }
        }

        throw new RuntimeException("Apollo config not found, param:" + param);
    }
}
