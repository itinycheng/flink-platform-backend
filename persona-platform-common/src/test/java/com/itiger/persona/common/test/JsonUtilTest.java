package com.itiger.persona.common.test;

import com.itiger.persona.common.entity.job.ExecutionMode;
import com.itiger.persona.common.entity.job.Sql;
import com.itiger.persona.common.entity.job.SqlContext;
import com.itiger.persona.common.entity.job.SqlType;
import com.itiger.persona.common.util.JsonUtil;
import org.junit.Test;

import java.util.Collections;

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

        SqlContext sqlContext1 = JsonUtil.toJson(jsonString, SqlContext.class);
        System.out.println(sqlContext1);

    }
}
