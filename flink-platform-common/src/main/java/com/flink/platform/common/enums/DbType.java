package com.flink.platform.common.enums;

import lombok.Getter;

/** database type. */
@Getter
public enum DbType {
    CLICKHOUSE("ru.yandex.clickhouse.ClickHouseDriver", "SELECT 1"),
    MYSQL("com.mysql.jdbc.Driver", "SELECT 1"),
    HIVE("org.apache.hive.jdbc.HiveDriver", "SELECT 1");

    private final String driver;

    private final String connTestQuery;

    DbType(String driver, String connTestQuery) {
        this.driver = driver;
        this.connTestQuery = connTestQuery;
    }
}
