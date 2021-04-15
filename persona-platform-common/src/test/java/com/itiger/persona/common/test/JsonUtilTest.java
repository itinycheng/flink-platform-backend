package com.itiger.persona.common.test;

import com.itiger.persona.common.job.ExecutionMode;
import com.itiger.persona.common.job.SqlContext;
import com.itiger.persona.common.util.JsonUtil;
import org.junit.Test;

import java.util.Collections;

public class JsonUtilTest {

    @Test
    public void testEnum() {
        SqlContext sqlContext = new SqlContext();
        sqlContext.setId("a");
        sqlContext.setSqls(Collections.singletonList("select"));
        sqlContext.setExecMode(ExecutionMode.BATCH);

        String jsonString = JsonUtil.toJsonString(sqlContext);
        System.out.println(jsonString);

        SqlContext sqlContext1 = JsonUtil.toJson(jsonString, SqlContext.class);
        System.out.println(sqlContext1);

    }
}
