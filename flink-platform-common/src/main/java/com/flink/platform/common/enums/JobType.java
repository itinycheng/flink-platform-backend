package com.flink.platform.common.enums;

import com.flink.platform.common.constants.Constant;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

import static com.flink.platform.common.constants.Constant.FLINK;
import static com.flink.platform.common.constants.Constant.JAVA;
import static com.flink.platform.common.constants.Constant.SQL;
import static com.flink.platform.common.enums.DbType.CLICKHOUSE;
import static java.util.stream.Collectors.toList;

/** job type. */
@Getter
public enum JobType {
    FLINK_SQL(FLINK, null),
    FLINK_JAR(FLINK, null),
    COMMON_JAR(JAVA, null),
    CLICKHOUSE_SQL(SQL, CLICKHOUSE),
    SHELL(Constant.SHELL, null);

    private final String classification;

    private final DbType dbType;

    JobType(String classification, DbType dbType) {
        this.classification = classification;
        this.dbType = dbType;
    }

    public static List<JobType> from(String classification) {
        return Arrays.stream(values())
                .filter(jobType -> jobType.classification.equals(classification))
                .collect(toList());
    }
}
