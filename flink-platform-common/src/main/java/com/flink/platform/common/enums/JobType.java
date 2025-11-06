package com.flink.platform.common.enums;

import com.flink.platform.common.constants.Constant;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

import static com.flink.platform.common.constants.Constant.FLINK;
import static com.flink.platform.common.constants.Constant.JAVA;
import static com.flink.platform.common.constants.Constant.SQL;
import static com.flink.platform.common.enums.DbType.CLICKHOUSE;
import static com.flink.platform.common.enums.DbType.HIVE;
import static com.flink.platform.common.enums.DbType.MYSQL;
import static java.util.stream.Collectors.toList;

/** job type. */
@Getter
public enum JobType {
    FLINK_SQL(FLINK, null),
    FLINK_JAR(FLINK, null),
    COMMON_JAR(JAVA, null),
    CLICKHOUSE_SQL(SQL, CLICKHOUSE),
    MYSQL_SQL(SQL, MYSQL),
    HIVE_SQL(SQL, HIVE),

    SHELL(Constant.SHELL, null),
    CONDITION(Constant.CONDITION, null),

    DEPENDENT(Constant.DEPENDENT, null),
    SUB_FLOW(Constant.SUB_FLOW, null),
    ;

    private final String classification;

    private final DbType dbType;

    JobType(String classification, DbType dbType) {
        this.classification = classification;
        this.dbType = dbType;
    }

    public static JobType from(String name) {
        for (JobType value : values()) {
            if (value.name().equals(name)) {
                return value;
            }
        }

        throw new IllegalArgumentException("Unknown job type: " + name);
    }

    public static List<JobType> fromClassification(String classification) {
        return Arrays.stream(values())
                .filter(jobType -> jobType.classification.equals(classification))
                .collect(toList());
    }
}
