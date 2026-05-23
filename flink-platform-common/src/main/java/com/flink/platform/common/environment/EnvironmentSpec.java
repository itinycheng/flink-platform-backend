package com.flink.platform.common.environment;

import lombok.Data;

@Data
public class EnvironmentSpec {
    private final EnvironmentType type;
    private final String name;
}
