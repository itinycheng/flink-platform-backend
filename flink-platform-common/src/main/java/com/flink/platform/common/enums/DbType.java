package com.flink.platform.common.enums;

import lombok.Getter;

/** database type. */
@Getter
public enum DbType {
    CLICKHOUSE("com.alibaba.druid.support.clickhouse.BalancedClickhouseDriver"),
    MYSQL("com.mysql.jdbc.Driver"),
    HIVE("org.apache.hive.jdbc.HiveDriver");

    private final String driver;

    DbType(String driver) {
        this.driver = driver;
    }
}
