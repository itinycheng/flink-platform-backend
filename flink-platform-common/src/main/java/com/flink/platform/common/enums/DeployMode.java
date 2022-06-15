package com.flink.platform.common.enums;

/** deploy mode. */
public enum DeployMode {

    /** deploy mode. */
    RUN_LOCAL("", ""),
    FLINK_YARN_PER("run", "yarn-per-job"),
    FLINK_YARN_SESSION("run", "yarn-session"),
    FLINK_YARN_RUN_APPLICATION("run-application", "yarn-application");

    public final String mode;

    public final String target;

    DeployMode(String mode, String target) {
        this.mode = mode;
        this.target = target;
    }

    public static DeployMode from(String name) {
        for (DeployMode value : values()) {
            if (value.name().equals(name)) {
                return value;
            }
        }

        throw new IllegalArgumentException("Unknown deploy mode: " + name);
    }
}
