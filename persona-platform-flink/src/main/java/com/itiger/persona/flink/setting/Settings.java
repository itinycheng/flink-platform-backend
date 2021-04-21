package com.itiger.persona.flink.setting;

import com.itiger.persona.common.exception.FlinkJobGenException;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

/**
 * loading setting.yml
 *
 * @author tiny.wang
 */

public class Settings {

    private static final String SETTING_FILE_PATH = "setting.yml";

    public static Setting getDefault() {
        try {
            InputStream resourceAsStream = Settings.class.getClassLoader().getResourceAsStream(SETTING_FILE_PATH);
            Yaml yaml = new Yaml();
            return yaml.loadAs(resourceAsStream, Setting.class);
        } catch (Exception e) {
            throw new FlinkJobGenException("cannot load setting.yml", e);
        }
    }

    /**
     * TODO merge configs into a new setting instance
     */
    public static Setting mergeWithSetting(Map<String, String> configs) {
        Setting defaultSetting = getDefault();
        return new Setting();
    }

}
