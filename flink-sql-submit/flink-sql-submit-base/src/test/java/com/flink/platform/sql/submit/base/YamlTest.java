package com.flink.platform.sql.submit.base;

import com.flink.platform.common.enums.ExecutionMode;
import com.flink.platform.sql.submit.base.common.ConfigLoader;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** Unit test for simple App. */
public class YamlTest {

    @Test
    public void mergeConfig() {
        Map<String, String> defaultConfig = ConfigLoader.loadDefault(ExecutionMode.BATCH);
        Map<String, String> configMap = new HashMap<>();
        configMap.put("a", "1");
        configMap.put("parallelism.default", "2");
        defaultConfig.putAll(configMap);
        defaultConfig.forEach((s, s2) -> System.out.println(s + ", " + s2));
    }
}
