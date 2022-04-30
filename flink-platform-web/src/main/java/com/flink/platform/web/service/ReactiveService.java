package com.flink.platform.web.service;

import com.flink.platform.common.enums.DbType;
import com.flink.platform.common.enums.SqlType;
import com.flink.platform.dao.entity.Datasource;
import com.flink.platform.dao.entity.ds.DatasourceParam;
import com.flink.platform.web.entity.request.ReactiveRequest;
import com.flink.platform.web.entity.vo.ReactiveDataVo;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import static com.flink.platform.common.enums.SqlType.SELECT;
import static com.flink.platform.common.util.SqlUtil.limitRowNum;

/** manage datasource service. */
@Service
public class ReactiveService {

    private static final Map<String, List<String>> EXEC_RESULT_BUFFER = new ConcurrentHashMap<>();

    public ReactiveDataVo executeAndGet(ReactiveRequest reactiveRequest, Datasource datasource)
            throws Exception {
        String content = reactiveRequest.getContent();
        if (isQuery(reactiveRequest.getContent())) {
            content = limitRowNum(reactiveRequest.getContent());
        }

        try (Connection connection = getConnection(datasource.getType(), datasource.getParams());
                Statement stmt = connection.createStatement()) {
            String[] columnNames;
            List<Object[]> dataList = new ArrayList<>();
            if (stmt.execute(content)) {
                ResultSet resultSet = stmt.getResultSet();
                ResultSetMetaData metaData = resultSet.getMetaData();

                // metadata.
                int num = metaData.getColumnCount();
                columnNames = new String[num];
                for (int i = 1; i <= num; i++) {
                    columnNames[i - 1] = metaData.getColumnName(i);
                }

                // data list.
                DbType dbType = datasource.getType();
                while (resultSet.next()) {
                    Object[] item = new Object[num];
                    for (int i = 1; i <= num; i++) {
                        item[i - 1] = toJavaObject(dbType, resultSet.getObject(i));
                    }
                    dataList.add(item);
                }
            } else {
                columnNames = new String[] {"success"};
                dataList.add(new Object[] {false});
            }

            return new ReactiveDataVo(columnNames, dataList, null);
        }
    }

    public Connection getConnection(DbType dbType, DatasourceParam params) throws Exception {
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

    public Object toJavaObject(DbType dbType, Object dbObject) throws Exception {
        switch (dbType) {
            case CLICKHOUSE:
                if (dbObject instanceof Array) {
                    Object objectArray = ((Array) dbObject).getArray();
                    int arrayLength = java.lang.reflect.Array.getLength(objectArray);
                    Object[] javaObjectArray = new Object[arrayLength];
                    for (int i = 0; i < arrayLength; i++) {
                        javaObjectArray[i] =
                                toJavaObject(dbType, java.lang.reflect.Array.get(objectArray, i));
                    }
                    return javaObjectArray;
                } else {
                    return dbObject;
                }
            case MYSQL:
                return dbObject;
            default:
                throw new RuntimeException("unsupported database type:" + dbType);
        }
    }

    public boolean isQuery(String sql) {
        return SqlType.parse(sql).getType() == SELECT;
    }
}
