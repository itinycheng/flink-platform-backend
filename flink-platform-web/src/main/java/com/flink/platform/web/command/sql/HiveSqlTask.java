package com.flink.platform.web.command.sql;

import com.flink.platform.dao.entity.Datasource;
import com.flink.platform.dao.entity.result.JobCallback;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.hive.jdbc.HiveStatement;

import java.util.List;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;

@Slf4j
public class HiveSqlTask extends SqlTask {

    private static final int DEFAULT_QUERY_LOG_INTERVAL = 1000;

    private final StringBuffer logBuffer;

    public HiveSqlTask(long jobRunId, @Nonnull List<String> sqlList, @Nonnull Datasource datasource) {
        super(jobRunId, sqlList, datasource);
        logBuffer = new StringBuffer();
    }

    @Override
    public JobCallback buildResult() {
        JobCallback callback = super.buildResult();
        callback.setMessage(callback.getMessage() + "\n### Log: ###\n" + logBuffer.toString());
        return callback;
    }

    @Override
    public void beforeExecSql() {
        try {
            Thread logThread = Thread.ofVirtual().unstarted(this::storeLog);
            logThread.start();
        } catch (Exception e) {
            log.error("start hive log collect thread failed", e);
        }
    }

    private void storeLog() {
        try {
            HiveStatement statement = (HiveStatement) getStatement();
            while (logBuffer.length() < 60_000 && statement.hasMoreLogs()) {
                for (String log : statement.getQueryLog()) {
                    logBuffer.append(log).append(LINE_SEPARATOR);
                }
                Thread.sleep(DEFAULT_QUERY_LOG_INTERVAL);
            }
        } catch (Exception e) {
            log.error("get hive query log error", e);
        }
    }
}
