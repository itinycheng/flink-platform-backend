package com.flink.platform.web.command.sql;

import com.flink.platform.common.enums.DbType;
import com.flink.platform.common.util.ExceptionUtil;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.Datasource;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.web.command.AbstractTask;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

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
import static com.flink.platform.common.util.Preconditions.checkNotNull;
import static com.flink.platform.web.util.JdbcUtil.createConnection;
import static com.flink.platform.web.util.JdbcUtil.toJavaObject;

/** SQL Task. */
@Slf4j
@Getter
@Setter
public class SqlTask extends AbstractTask {

    private final Datasource datasource;

    private final List<String> sqlList;

    private volatile Statement statement;

    private boolean isSucceed;

    private String exceptionStack;

    private String sqlResult;

    public SqlTask(long jobRunId, @Nonnull List<String> sqlList, @Nonnull Datasource datasource) {
        super(jobRunId);
        this.sqlList = checkNotNull(sqlList);
        this.datasource = checkNotNull(datasource);
    }

    @Override
    public void run() throws Exception {
        Exception exception = null;
        List<Map<String, Object>> dataList = new ArrayList<>();
        try (Connection connection = createConnection(datasource.getType(), datasource.getParams());
                Statement stmt = connection.createStatement()) {
            this.statement = stmt;

            String executingSql = null;
            try {
                for (int j = 0; j < sqlList.size(); j++) {
                    executingSql = sqlList.get(j);

                    // execute sql.
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
                                itemMap.put(columnNames[i - 1], toJavaObject(resultSet.getObject(i), dbType));
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

        this.isSucceed = exceptionMsg == null;
        this.exceptionStack = exceptionMsg;
        this.sqlResult = JsonUtil.toJsonString(dataList);
    }

    @Override
    public void cancel() {
        if (statement != null) {
            try {
                statement.cancel();
            } catch (Exception e) {
                log.error("Cancel jdbc statement failed", e);
            }
        }
    }

    public JobCallback buildResult() {
        return new JobCallback(
                isSucceed ? sqlResult : String.join(LINE_SEPARATOR, sqlResult, exceptionStack),
                isSucceed ? SUCCESS : FAILURE);
    }
}
