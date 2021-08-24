package com.flink.platform.common.test;

import com.flink.platform.common.job.Catalog;
import com.flink.platform.common.job.Sql;
import com.flink.platform.common.job.SqlContext;
import com.flink.platform.common.enums.ExecutionMode;
import com.flink.platform.common.enums.SqlType;
import com.flink.platform.common.util.JsonUtil;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonUtilTest {

    @Test
    public void testEnum() {
        SqlContext sqlContext = new SqlContext();
        sqlContext.setId("a");

        Sql sql = new Sql();
        sql.setType(SqlType.INSERT_INTO);
        sql.setOperands(new String[]{"insert into table"});
        sqlContext.setSqls(Collections.singletonList(sql));

        sqlContext.setExecMode(ExecutionMode.BATCH);

        String jsonString = JsonUtil.toJsonString(sqlContext);
        System.out.println(jsonString);

        SqlContext sqlContext1 = JsonUtil.toBean(jsonString, SqlContext.class);
        System.out.println(sqlContext1);

    }

    @Test
    public void string() {
        Map<String, String> map = new HashMap<>();
        map.put("a", "true");
        map.put("b", null);
        Catalog catalog = new Catalog();
        catalog.setConfigs(map);
        String str = JsonUtil.toJsonString(catalog);
        System.out.println(str);
    }

    @Test
    public void test0() {
        String listString = "[{\"symbol\":\"BILI\",\"costLevel\":1,\"strike\":\"\",\"currency\":\"USD\",\"expiry\":\"\",\"right\":\"\",\"status\":\"LONG\",\"timestamp\":1621990862104},{\"symbol\":\"BIDU\",\"costLevel\":1,\"strike\":\"\",\"currency\":\"USD\",\"expiry\":\"\",\"right\":\"\",\"status\":\"LONG\",\"timestamp\":1621990862101}]";
        List<String> strings = JsonUtil.toList(listString);
        System.out.println(strings);
    }
}
