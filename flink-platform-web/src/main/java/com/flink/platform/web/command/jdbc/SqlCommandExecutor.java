package com.flink.platform.web.command.jdbc;

import com.flink.platform.common.enums.DbType;
import com.flink.platform.dao.entity.Datasource;
import com.flink.platform.dao.service.DatasourceService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCallback;
import com.flink.platform.web.command.JobCommand;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.web.util.JdbcUtil.createConnection;
import static com.flink.platform.web.util.JdbcUtil.toJavaObject;

/** SQL command executor. */
@Slf4j
@Component("sqlCommandExecutor")
public class SqlCommandExecutor implements CommandExecutor {

    @Autowired private DatasourceService datasourceService;

    @Autowired private JobRunInfoService jobRunInfoService;

    @Override
    public boolean isSupported(JobCommand jobCommand) {
        return jobCommand instanceof SqlCommand;
    }

    @Override
    public JobCallback execCommand(JobCommand command) throws Exception {
        SqlCommand sqlCommand = (SqlCommand) command;
        Datasource datasource = datasourceService.getById(sqlCommand.getDsId());

        Exception exception = null;
        List<String> dataList = new ArrayList<>();
        try (Connection connection =
                        createConnection(datasource.getType(), datasource.getParams());
                Statement stmt = connection.createStatement()) {
            String executingSql = null;
            try {
                for (String sql : sqlCommand.getSqls()) {
                    executingSql = sql;
                    if (!stmt.execute(sql)) {
                        dataList.add(String.format("exec: %s, result: %s", sql, false));
                        continue;
                    }

                    try (ResultSet resultSet = stmt.getResultSet()) {
                        // metadata.
                        ResultSetMetaData metaData = resultSet.getMetaData();
                        int num = metaData.getColumnCount();
                        String[] columnNames = new String[num];
                        for (int i = 1; i <= num; i++) {
                            columnNames[i - 1] = metaData.getColumnName(i);
                        }
                        dataList.add(ArrayUtils.toString(columnNames));

                        // data list.
                        DbType dbType = datasource.getType();
                        while (resultSet.next()) {
                            Object[] item = new Object[num];
                            for (int i = 1; i <= num; i++) {
                                item[i - 1] = toJavaObject(resultSet.getObject(i), dbType);
                            }
                            dataList.add(ArrayUtils.toString(item));
                        }
                    }

                    log.info("Execute sql successfully: {}", sql);
                }
            } catch (Exception e) {
                exception = e;
                log.error("Execute sql: {} failed.", executingSql, e);
            }
        }

        String exceptionMsg = null;
        if (exception != null) {
            StringWriter writer = new StringWriter();
            exception.printStackTrace(new PrintWriter(writer, true));
            exceptionMsg = writer.toString();
        }

        boolean isSucceed = exceptionMsg == null;
        return new JobCallback(
                null,
                null,
                isSucceed
                        ? dataList.toString()
                        : String.join(LINE_SEPARATOR, dataList.toString(), exceptionMsg),
                isSucceed ? SUCCESS : FAILURE);
    }
}
