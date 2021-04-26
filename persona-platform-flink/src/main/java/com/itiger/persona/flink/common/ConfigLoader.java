package com.itiger.persona.flink.common;

import com.itiger.persona.common.exception.FlinkJobGenException;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

/**
 * @author tiny.wang
 */
public class ConfigLoader {

    private static final String DEFAULT_CONFIG = "flink-default.yaml";

    public static Map<String, String> loadDefault() {
        try {
            InputStream resourceAsStream =
                    ConfigLoader.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIG);
            Yaml yaml = new Yaml();
            return yaml.load(resourceAsStream);
        } catch (Exception e) {
            throw new FlinkJobGenException("cannot load flink-default.yml", e);
        }
    }
}
