package com.flink.platform.common.enums;

/**
 * inherit param mode.
 */
public enum InheritParamMode {
    ALLOW,
    DENY,
    CUSTOM;

    public static boolean isInheritAllowed(InheritParamMode mode) {
        return mode != DENY;
    }
}
