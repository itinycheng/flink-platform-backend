package com.flink.platform.dao.entity.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FlinkConfig extends BaseConfig {

    private String commandPath;

    // rename to sqlEntryJar;
    private String jarFile;

    // rename to sqlEntryClass;
    private String className;

    private String libDirs;
}
