package com.flink.platform.web.util;

import com.flink.platform.common.enums.DbType;
import com.flink.platform.dao.entity.ds.DatasourceParam;
import org.apache.commons.collections4.MapUtils;

import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Jdbc util. */
public class JdbcUtil {

    /** create datasource. */
    public static Connection createConnection(DbType dbType, DatasourceParam params) throws Exception {
        Class.forName(dbType.getDriver());
        Properties properties = new Properties();

        switch (dbType) {
            case CLICKHOUSE:
            case MYSQL:
            case HIVE:
                properties.setProperty("user", PlaceholderUtil.apolloConfig(params.getUsername()));
                String password = PlaceholderUtil.apolloConfig(params.getPassword());
                if (password != null) {
                    properties.setProperty("password", password);
                }
                break;
            default:
                throw new RuntimeException("unsupported db type: " + dbType);
        }

        if (MapUtils.isNotEmpty(params.getProperties())) {
            properties.putAll(params.getProperties());
        }

        // temporary solution for hive `java.net.SocketTimeoutException: Read timed out`.
        if (DbType.HIVE.equals(dbType)) {
            DriverManager.setLoginTimeout(600);
        }

        return DriverManager.getConnection(params.getUrl(), properties);
    }

    public static Object toJavaObject(Object dbObject, DbType dbType) throws SQLException {
        switch (dbType) {
            case CLICKHOUSE:
                if (dbObject instanceof Array) {
                    Object objectArray = ((Array) dbObject).getArray();
                    int arrayLength = java.lang.reflect.Array.getLength(objectArray);
                    Object[] javaObjectArray = new Object[arrayLength];
                    for (int i = 0; i < arrayLength; i++) {
                        javaObjectArray[i] = toJavaObject(java.lang.reflect.Array.get(objectArray, i), dbType);
                    }
                    return javaObjectArray;
                } else {
                    return dbObject;
                }
            case MYSQL:
            case HIVE:
            default:
                return dbObject;
        }
    }
}
