package com.flink.platform.common.test;

import com.fasterxml.jackson.databind.type.CollectionLikeType;
import com.flink.platform.common.enums.ExecutionMode;
import com.flink.platform.common.enums.SqlType;
import com.flink.platform.common.job.Sql;
import com.flink.platform.common.job.SqlContext;
import com.flink.platform.common.util.JsonUtil;
import lombok.Data;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** json test. */
public class JsonUtilTest {

    @Test
    public void testEnum() {
        SqlContext sqlContext = new SqlContext();
        sqlContext.setId("a");

        Sql sql = new Sql();
        sql.setType(SqlType.INSERT_INTO);
        sql.setOperands(new String[] {"insert into table"});
        sqlContext.setSqls(Collections.singletonList(sql));

        sqlContext.setExecMode(ExecutionMode.BATCH);

        String jsonString = JsonUtil.toJsonString(sqlContext);
        System.out.println(jsonString);

        SqlContext sqlContext1 = JsonUtil.toBean(jsonString, SqlContext.class);
        System.out.println(sqlContext1);
    }

    @Test
    public void test() {
        String listString =
                "[{\"symbol\":\"BILI\",\"costLevel\":1,\"strike\":\"\",\"currency\":\"USD\",\"expiry\":\"\",\"right\":\"\",\"status\":\"LONG\",\"timestamp\":1621990862104},{\"symbol\":\"BIDU\",\"costLevel\":1,\"strike\":\"\",\"currency\":\"USD\",\"expiry\":\"\",\"right\":\"\",\"status\":\"LONG\",\"timestamp\":1621990862101}]";
        CollectionLikeType positionListType =
                JsonUtil.MAPPER.getTypeFactory().constructCollectionLikeType(List.class, PositionLabel.class);
        List<PositionLabel> objList = JsonUtil.toList(listString, positionListType);
        System.out.println(objList);

        String str = JsonUtil.toJsonString(objList);
        System.out.println(str);
    }

    @Test
    public void test0() {
        String listString = "[\"symbol\",\"BILI\"]";
        List<String> strings = JsonUtil.toList(listString);
        System.out.println(strings);
    }

    @Test
    public void test1() {
        String json =
                "{\"symbol\":\"BILI\",\"costLevel\":1,\"strike\":\"\",\"currency\":\"USD\",\"expiry\":\"\",\"right\":\"\",\"status\":\"LONG\",\"timestamp\":1621990862104}";
        Map<String, Object> map = JsonUtil.toMap(json);
        System.out.println(map);
    }

    @Test
    public void test2() {
        String json =
                "{\"symbol\":\"BILI\",\"costLevel\":1,\"strike\":\"\",\"currency\":\"USD\",\"expiry\":\"\",\"right\":\"\",\"status\":\"LONG\",\"timestamp\":1621990862104}";
        Map<String, String> strMap = JsonUtil.toStrMap(json);
        System.out.println(strMap);
    }

    /** data. */
    @Data
    public static class PositionLabel {

        private String symbol;
        private String expiry;
        private String strike;
        private String right;
        private String currency;
        private Integer costLevel;
        private String status;
        private Long timestamp;
    }
}
