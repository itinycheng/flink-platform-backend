package com.flink.platform.web.command.sql;

import com.flink.platform.common.enums.DbType;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.util.ExceptionUtil;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.Datasource;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.dao.service.DatasourceService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.common.enums.JobType.CLICKHOUSE_SQL;
import static com.flink.platform.common.enums.JobType.HIVE_SQL;
import static com.flink.platform.common.enums.JobType.MYSQL_SQL;
import static com.flink.platform.web.util.JdbcUtil.createConnection;
import static com.flink.platform.web.util.JdbcUtil.toJavaObject;

/** SQL command executor. */
@Slf4j
@Component("sqlCommandExecutor")
public class SqlCommandExecutor implements CommandExecutor {

    @Autowired private DatasourceService datasourceService;

    @Autowired private JobRunInfoService jobRunInfoService;

    @Override
    public boolean isSupported(JobType jobType) {
        return jobType == CLICKHOUSE_SQL || jobType == MYSQL_SQL || jobType == HIVE_SQL;
    }

    @Nonnull
    @Override
    public JobCallback execCommand(@Nonnull JobCommand command) throws Exception {
        SqlCommand sqlCommand = (SqlCommand) command;
        Datasource datasource = datasourceService.getById(sqlCommand.getDsId());

        Exception exception = null;
        List<Map<String, Object>> dataList = new ArrayList<>();
        try (Connection connection =
                        createConnection(datasource.getType(), datasource.getParams());
                Statement stmt = connection.createStatement()) {
            String executingSql = null;
            try {
                for (int j = 0; j < sqlCommand.getSqls().size(); j++) {
                    executingSql = sqlCommand.getSqls().get(j);

                    // execute sq.
                    if (!stmt.execute(executingSql)) {
                        Map<String, Object> itemMap = new HashMap<>(1);
                        itemMap.put("_no_result_" + j, String.format("executed: %s", executingSql));
                        dataList.add(itemMap);
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

                        // data list.
                        DbType dbType = datasource.getType();
                        int count = 0;
                        while (resultSet.next() && count++ < 2000) {
                            Map<String, Object> itemMap = new HashMap<>(num);
                            for (int i = 1; i <= num; i++) {
                                itemMap.put(
                                        columnNames[i - 1],
                                        toJavaObject(resultSet.getObject(i), dbType));
                            }
                            dataList.add(itemMap);
                        }
                    }

                    log.info("Execute sql successfully: {}", executingSql);
                }
            } catch (Exception e) {
                exception = e;
                log.error("Execute sql: {} failed.", executingSql, e);
            }
        }

        String exceptionMsg = null;
        if (exception != null) {
            exceptionMsg = ExceptionUtil.stackTrace(exception);
        }

        boolean isSucceed = exceptionMsg == null;
        String dataString = JsonUtil.toJsonString(dataList);
        return new JobCallback(
                isSucceed ? dataString : String.join(LINE_SEPARATOR, dataString, exceptionMsg),
                isSucceed ? SUCCESS : FAILURE);
    }
}
