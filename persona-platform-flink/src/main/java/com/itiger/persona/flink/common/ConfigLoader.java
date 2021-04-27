package com.itiger.persona.flink.common;

import com.itiger.persona.common.exception.FlinkJobGenException;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

import static java.util.stream.Collectors.toMap;

/**
 * @author tiny.wang
 */
public class ConfigLoader {

    private static final String DEFAULT_CONFIG = "flink-default.yaml";

    public static Map<String, String> loadDefault() {
        try {
            InputStream resourceAsStream =
                    ConfigLoader.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIG);
            Map<String, Object> configMap = new Yaml().load(resourceAsStream);
            return configMap.entrySet().stream()
                    .filter(entry -> Objects.nonNull(entry.getValue()))
                    .collect(toMap(Map.Entry::getKey, entry -> entry.getValue().toString()));
        } catch (Exception e) {
            throw new FlinkJobGenException("cannot load flink-default.yml", e);
        }
    }
}
