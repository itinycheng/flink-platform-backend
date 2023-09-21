package com.flink.platform.sql.submit.base;

import com.flink.platform.common.enums.ExecutionMode;
import com.flink.platform.common.exception.FlinkJobGenException;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import static java.util.stream.Collectors.toMap;

/** load default flink config. */
public class ConfigLoader {

    private static final String DEFAULT_CONFIG = "flink-default.yaml";

    public static Map<String, String> loadDefault(ExecutionMode execMode) {
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
}
