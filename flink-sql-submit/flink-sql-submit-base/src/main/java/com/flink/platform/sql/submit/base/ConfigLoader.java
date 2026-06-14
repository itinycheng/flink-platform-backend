package com.flink.platform.sql.submit.base;

import com.flink.platform.common.enums.ExecutionMode;
import com.flink.platform.common.exception.FlinkJobGenException;
import com.flink.platform.common.job.SqlContext;
import lombok.val;
import org.apache.commons.text.StringSubstitutor;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.stream.Collectors.toMap;

/** Load flink-default.yaml and resolve {@code {{var}}} placeholders against a SqlContext. */
public class ConfigLoader {

    private static final String DEFAULT_CONFIG = "flink-default.yaml";

    public static Map<String, String> loadDefault(ExecutionMode execMode) {
        return resolve(readYaml(execMode), Collections.emptyMap());
    }

    public static Map<String, String> loadDefault(ExecutionMode execMode, SqlContext sqlContext) {
        return resolve(readYaml(execMode), buildVars(sqlContext));
    }

    private static Map<String, String> readYaml(ExecutionMode execMode) {
        try {
            InputStream resourceAsStream = ConfigLoader.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIG);
            Map<String, Map<String, Object>> configMap = new Yaml().load(resourceAsStream);
            return configMap.getOrDefault(execMode.name().toLowerCase(), Collections.emptyMap()).entrySet().stream()
                    .filter(entry -> Objects.nonNull(entry.getKey()) && Objects.nonNull(entry.getValue()))
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
