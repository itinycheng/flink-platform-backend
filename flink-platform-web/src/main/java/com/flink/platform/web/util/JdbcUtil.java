package com.flink.platform.web.util;

import com.flink.platform.common.enums.DbType;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.ds.DatasourceParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.StringJoiner;

/** Jdbc util. */
@Slf4j
public class JdbcUtil {

    /** create datasource. */
    public static Connection createConnection(DbType dbType, DatasourceParam params, JobRunInfo jobRunInfo) throws Exception {
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

        String url = addAppName(params.getUrl(), jobRunInfo);
        log.info("create connection url: {}", url);

        // avoid hive connection creation timeout.
        DriverManager.setLoginTimeout(600);
        return DriverManager.getConnection(url, properties);
    }

    private static String addAppName(String url, JobRunInfo jobRunInfo) {
        if (StringUtils.isBlank(url)) {
            return url;
        }
        // only handle kyuubi type url.
        String[] ss = StringUtils.split(url, "#");
        if (!StringUtils.containsIgnoreCase(ss[0], "kyuubi")) {
            return url;
        }
        String sparkAppName = createAppName(jobRunInfo);
        if (ss.length == 1) {
            return url + "#spark.app.name=" + sparkAppName;
        }
        String[] params = StringUtils.split(ss[1], ";");
        boolean hasAppNameConfig = false;
        StringJoiner sj = new StringJoiner(";", ss[0] + "#", "");
        for (String param : params) {
            sj.add(param);
            if (StringUtils.startsWith(param, "spark.app.name")) {
                hasAppNameConfig = true;
            }
        }
        if (!hasAppNameConfig) {
            sj.add("spark.app.name=" + sparkAppName);
        }
        return sj.toString();
    }

    private static String createAppName(JobRunInfo jobRun) {
        String jobName = jobRun.getName().replaceAll("\\s+", "");
        return String.join(
                "-", jobRun.getExecMode().name(), jobRun.getJobCode() + "_" + jobRun.getFlowRunId(),
                jobName, String.valueOf(jobRun.getUserId()));
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
