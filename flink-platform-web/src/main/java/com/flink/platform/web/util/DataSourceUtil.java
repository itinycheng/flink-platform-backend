package com.flink.platform.web.util;

import com.flink.platform.common.enums.DbType;
import com.flink.platform.dao.entity.ds.DatasourceParam;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

/** datasource util. */
public class DataSourceUtil {

    /** create datasource. */
    public static Connection createConnection(DbType dbType, DatasourceParam params)
            throws Exception {
        Class.forName(params.getDriver());
        Properties properties = new Properties();
        switch (dbType) {
            case CLICKHOUSE:
                properties.setProperty("user", params.getUsername());
                properties.setProperty("password", params.getPassword());
                break;
            case MYSQL:
            default:
                throw new RuntimeException("unsupported db type: " + dbType);
        }
        properties.putAll(params.getProperties());
        return DriverManager.getConnection(params.getUrl(), properties);
    }
}
