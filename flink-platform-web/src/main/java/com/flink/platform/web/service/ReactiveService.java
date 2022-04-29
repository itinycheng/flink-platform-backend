package com.flink.platform.web.service;

import com.flink.platform.common.enums.DbType;
import com.flink.platform.common.enums.SqlType;
import com.flink.platform.dao.entity.Datasource;
import com.flink.platform.dao.entity.ds.DatasourceParam;
import com.flink.platform.web.entity.request.ReactiveRequest;
import com.flink.platform.web.entity.vo.TableDataVo;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static com.flink.platform.common.enums.SqlType.SELECT;
import static com.flink.platform.common.util.SqlUtil.limitRowNum;

/** manage datasource service. */
@Service
public class ReactiveService {

    public TableDataVo executeAndGet(ReactiveRequest reactiveRequest, Datasource datasource)
            throws Exception {
        String content = reactiveRequest.getContent();
        if (isQuery(reactiveRequest.getContent())) {
            content = limitRowNum(reactiveRequest.getContent());
        }

        try (Connection connection = getConnection(datasource.getType(), datasource.getParams());
                Statement stmt = connection.createStatement()) {
            stmt.execute(content);
            ResultSet resultSet = stmt.getResultSet();
            ResultSetMetaData metaData = resultSet.getMetaData();

            // metadata.
            int num = metaData.getColumnCount();
            String[] columnNames = new String[num];
            for (int i = 1; i <= num; i++) {
                columnNames[i - 1] = metaData.getColumnName(i);
            }

            // data list.
            DbType dbType = datasource.getType();
            List<Object[]> dataList = new ArrayList<>();
            while (resultSet.next()) {
                Object[] item = new Object[num];
                for (int i = 1; i <= num; i++) {
                    item[i - 1] = toJavaObject(dbType, resultSet.getObject(i));
                }
                dataList.add(item);
            }

            return new TableDataVo(columnNames, dataList);
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
                    return ((Array) dbObject).getArray();
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
