package com.flink.platform.web.command.sql;

import com.flink.platform.dao.entity.Datasource;
import com.flink.platform.dao.entity.ds.DatasourceParam;
import com.flink.platform.dao.entity.result.JobCallback;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hive.jdbc.HiveStatement;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nonnull;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;

@Slf4j
public class HiveSqlTask extends SqlTask {

    private static final int DEFAULT_QUERY_LOG_INTERVAL = 1000;

    private final StringBuffer logBuffer;

    public HiveSqlTask(
            long jobRunId, @Nonnull List<String> sqlList, @Nonnull Datasource datasource) {
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
            // remove set spark conf sql
            this.getSqlList().removeIf(this::isSetSparkConf);
            log.info("exec sql list: {}", this.getSqlList());
            new Thread(this::storeLog).start();
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

    @VisibleForTesting
    protected Map<String, String> getSparkConf(List<String> sqlList) {
        Map<String, String> sparkConf = Maps.newLinkedHashMap();
        sparkConf.putAll(getDefaultSparkConf());
        if (CollectionUtils.isEmpty(sqlList)) {
            return sparkConf;
        }
        for (String sql : sqlList) {
            if (!isSetSparkConf(sql)) {
                continue;
            }
            sql = StringUtils.trim(sql);
            String kvs = StringUtils.trim(StringUtils.replaceIgnoreCase(sql, "set ", ""));
            String[] kv = StringUtils.split(kvs, "=");
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

    private Boolean isSetSparkConf(String sql) {
        if (StringUtils.isBlank(sql)) {
            return false;
        }
        sql = StringUtils.trim(sql);
        if (!StringUtils.startsWithIgnoreCase(sql, "set")) {
            return false;
        }
        String confVal = StringUtils.trim(StringUtils.replaceIgnoreCase(sql, "set ", ""));
        if (!StringUtils.startsWithIgnoreCase(confVal, "spark")) {
            return false;
        }
        return StringUtils.split(confVal, "=").length == 2;
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
