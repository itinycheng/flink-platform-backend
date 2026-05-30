package com.flink.platform.common.util;

import lombok.Data;
import software.amazon.awssdk.utils.SystemSetting;

@Data
public final class CustomSystemSetting implements SystemSetting {

    private final String property;
    private final String environmentVariable;

    @Override
    public String property() {
        return property;
    }

    @Override
    public String environmentVariable() {
        return environmentVariable;
    }

    @Override
    public String defaultValue() {
        return null;
    }
}
