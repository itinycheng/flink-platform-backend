package com.flink.platform.web.command.sql;

import com.flink.platform.dao.entity.Datasource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

@Slf4j
class HiveSqlTaskTest {

    @Test
    void getSparkConfTest() {
        runSparkConfFlow(List.of("set spark.dynamicAllocation.maxExecutors=10", "select 1"));
    }

    @Test
    void getSparkConfTest2() {
        runSparkConfFlow(List.of(
                "create xxx;", "SET spark.sql.shuffle.partitions = 1;", "create tera;", "insert overwrite select 1;"));
    }

    private static void runSparkConfFlow(List<String> sqlList) {
        var mutableSqlList = new ArrayList<>(sqlList);
        var task = new HiveSqlTask(1L, mutableSqlList, new Datasource());
        log.info("sparkConf: {}", task.getSparkConf(mutableSqlList));
        task.beforeExecSql();
        log.info("execSqlList: {}", task.getSqlList());
    }
}
