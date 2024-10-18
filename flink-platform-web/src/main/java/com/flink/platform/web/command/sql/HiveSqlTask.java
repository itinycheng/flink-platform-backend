package com.flink.platform.web.command.sql;

import com.flink.platform.dao.entity.Datasource;
import com.flink.platform.dao.entity.ds.DatasourceParam;
import com.flink.platform.dao.entity.result.JobCallback;
import com.google.common.collect.Maps;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hive.jdbc.HiveStatement;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

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

    @Override
    public DatasourceParam getDatasourceParam() {
        // sql set conf(spark conf) -> connection param
        List<String> sqlList = super.getSqlList();
        Datasource datasource = super.getDatasource();
        DatasourceParam datasourceParam = datasource.getParams();
        String datasourceUrl = datasourceParam.getUrl();
        Map<String, String> sparkConf = getSparkConf(sqlList);
        // jdbc:hive2://<host>:<port>/<dbName>;<sessionVars>?<kyuubiConfs>#<[spark|hive]Vars>
        boolean containsParamFlag = StringUtils.contains(datasourceUrl, "#");
        StringJoiner sparkConfStr = new StringJoiner(";", containsParamFlag ? ";" : "#", "");
        sparkConf.forEach((k, v) -> sparkConfStr.add(k + "=" + v));
        datasourceParam.setUrl(datasourceUrl + sparkConfStr);
        return datasourceParam;
    }

    private Map<String, String> getSparkConf(List<String> sqlList) {
        Map<String, String> sparkConf = Maps.newLinkedHashMap();
        sparkConf.putAll(getDefaultSparkConf());
        if (CollectionUtils.isEmpty(sqlList)) {
            return sparkConf;
        }
        for (String sql : sqlList) {
            if (StringUtils.isBlank(sql)) {
                continue;
            }
            sql = StringUtils.trim(sql);
            if (!StringUtils.startsWithIgnoreCase(sql, "set")) {
                break;
            }
            String kvs = StringUtils.trim(StringUtils.replaceIgnoreCase(sql, "set ", ""));
            String[] kv = StringUtils.split(kvs, "=");
            if (kv.length != 2) {
                continue;
            }
            // only set spark conf
            if (!StringUtils.startsWithIgnoreCase(StringUtils.trim(kv[0]), "spark")) {
                continue;
            }
            sparkConf.put(StringUtils.trim(kv[0]), StringUtils.trim(kv[1]));
        }
        return sparkConf;
    }

    private Map<String, String> getDefaultSparkConf() {
        Map<String, String> sparkDefaultConf = Maps.newLinkedHashMap();
        sparkDefaultConf.put("spark.app.name", getSparkAppName());
        // submit yarn queue
        // sparkDefaultVar.put("spark.yarn.queue", "root.users.hdfs");
        return sparkDefaultConf;
    }

    private String getSparkAppName() {
        return "BATCH-SPARK-" + super.getJobRunId() + "-" + System.currentTimeMillis();
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
