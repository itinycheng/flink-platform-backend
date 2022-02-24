package com.flink.platform.common.enums;

import com.flink.platform.common.constants.Constant;

import java.util.Arrays;
import java.util.List;

import static com.flink.platform.common.constants.Constant.FLINK;
import static com.flink.platform.common.constants.Constant.JAVA;
import static com.flink.platform.common.constants.Constant.SQL;
import static java.util.stream.Collectors.toList;

/** job type. */
public enum JobType {
    FLINK_SQL(FLINK),
    FLINK_JAR(FLINK),
    COMMON_JAR(JAVA),
    CLICKHOUSE_SQL(SQL),
    SHELL(Constant.SHELL);

    private final String type;

    JobType(String type) {
        this.type = type;
    }

    public static List<JobType> from(String type) {
        return Arrays.stream(values())
                .filter(jobType -> jobType.type.equals(type))
                .collect(toList());
    }
}
