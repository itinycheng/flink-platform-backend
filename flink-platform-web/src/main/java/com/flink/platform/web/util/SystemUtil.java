package com.flink.platform.web.util;

import java.util.HashMap;
import java.util.Map;

/**
 * SystemUtil.
 */
public class SystemUtil {

    public static Map<String, String> mergeEnv(Map<String, String> overrideVars) {
        // Inherit the current environment variables
        if (overrideVars == null || overrideVars.isEmpty()) {
            return null;
        }

        // override the environment variables with the given map
        var current = System.getenv();
        var merged = new HashMap<String, String>(current.size() + overrideVars.size());
        merged.putAll(current);
        merged.putAll(overrideVars);
        return merged;
    }

    public static String[] toEnvArray(Map<String, String> vars) {
        // Inherit the current environment variables
        if (vars == null || vars.isEmpty()) {
            return null;
        }

        return vars.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
    }
}
