package com.flink.platform.web.command.sql;

import com.flink.platform.dao.entity.Datasource;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.List;
import java.util.Map;

@Slf4j
public class HiveSqlTaskTest {

    @Test
    public void getSparkConfTest() {
        List<String> sqlList = Lists.newArrayList(
                "set spark.dynamicAllocation.maxExecutors=10",
                "select 1"
        );
        HiveSqlTask hiveSqlTask = new HiveSqlTask(1L, sqlList, new Datasource());
        Map<String, String> sparkConf = hiveSqlTask.getSparkConf(sqlList);
        log.info("sparkConf: {}", sparkConf);
        hiveSqlTask.beforeExecSql();
        List<String> execSqlList = hiveSqlTask.getSqlList();
        log.info("execSqlList: {}", execSqlList);
    }

    @Test
    public void getSparkConfTest2() {
        List<String> sqlList = Lists.newArrayList(
                "create xxx;",
                "SET spark.sql.shuffle.partitions = 1;",
                "create taera;",
                "insert overwrite select 1;"
        );
        HiveSqlTask hiveSqlTask = new HiveSqlTask(1L, sqlList, new Datasource());
        Map<String, String> sparkConf = hiveSqlTask.getSparkConf(sqlList);
        log.info("sparkConf: {}", sparkConf);
        hiveSqlTask.beforeExecSql();
        List<String> execSqlList = hiveSqlTask.getSqlList();
        log.info("execSqlList: {}", execSqlList);
    }

}
