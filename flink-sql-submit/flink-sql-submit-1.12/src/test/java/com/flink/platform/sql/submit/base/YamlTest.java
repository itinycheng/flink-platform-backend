package com.flink.platform.sql.submit.base;

import com.flink.platform.common.enums.ExecutionMode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit test for simple App. */
class YamlTest {

    @Test
    void mergeConfig() {
        Map<String, String> defaultConfig = ConfigLoader.loadDefault(ExecutionMode.BATCH);
        Map<String, String> configMap = new HashMap<>();
        configMap.put("parallelism.default", "2");
        defaultConfig.putAll(configMap);
        defaultConfig.forEach((s, s2) -> System.out.println(s + ", " + s2));
        assertEquals("2", defaultConfig.get("parallelism.default"));
    }
}
