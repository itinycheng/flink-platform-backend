package com.flink.platform.sql.submit.base;

import com.flink.platform.common.enums.ExecutionMode;
import com.flink.platform.common.exception.FlinkJobGenException;
import com.flink.platform.common.job.SqlContext;
import lombok.val;
import org.apache.commons.text.StringSubstitutor;
import org.yaml.snakeyaml.Yaml;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toMap;

/** Load flink-default.yaml and resolve {@code {{var}}} placeholders against a SqlContext. */
public class ConfigLoader {

    private static final String DEFAULT_CONFIG = "flink-default.yaml";

    public static Map<String, String> loadDefault(ExecutionMode execMode) {
        return resolve(readYaml(execMode), emptyMap());
    }

    public static Map<String, String> loadDefault(ExecutionMode execMode, SqlContext sqlContext) {
        return resolve(readYaml(execMode), buildVars(sqlContext));
    }

    private static Map<String, String> readYaml(ExecutionMode execMode) {
        try {
            val resourceAsStream = ConfigLoader.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIG);
            val configMap = new Yaml().<Map<String, Map<String, Object>>>load(resourceAsStream);
            return configMap.getOrDefault(execMode.name().toLowerCase(), emptyMap()).entrySet().stream()
                    .filter(entry -> nonNull(entry.getKey()) && nonNull(entry.getValue()))
                    .collect(toMap(Map.Entry::getKey, entry -> entry.getValue().toString()));
        } catch (Exception e) {
            throw new FlinkJobGenException("cannot load flink-default.yml", e);
        }
    }

    private static Map<String, String> buildVars(SqlContext sqlContext) {
        val vars = new HashMap<String, String>();
        vars.put("job", "job_" + sqlContext.getJobId());
        vars.put("workspace", "workspace_" + sqlContext.getWorkspaceId());
        return vars;
    }

    private static Map<String, String> resolve(Map<String, String> configMap, Map<String, String> vars) {
        if (configMap.isEmpty()) {
            return configMap;
        }

        val sub = new StringSubstitutor(vars)
                .setVariablePrefix("{{")
                .setVariableSuffix("}}")
                .setEnableUndefinedVariableException(true);
        val out = new HashMap<String, String>(configMap.size());
        for (val config : configMap.entrySet()) {
            val value = config.getValue();
            if (value == null) {
                continue;
            }

            try {
                out.put(config.getKey(), sub.replace(value));
            } catch (Exception e) {
                throw new FlinkJobGenException("Undefined variable in '" + config.getKey() + "=" + value + "'", e);
            }
        }

        return out;
    }
}
