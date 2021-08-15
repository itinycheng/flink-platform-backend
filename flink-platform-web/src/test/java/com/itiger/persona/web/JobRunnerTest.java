package com.itiger.persona.web;

import com.itiger.persona.common.util.JsonUtil;
import com.itiger.persona.enums.SqlVar;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import static com.itiger.persona.enums.SqlVar.CURRENT_TIMESTAMP;
import static com.itiger.persona.enums.SqlVar.CURRENT_TIME_MINUS;
import static java.util.stream.Collectors.toMap;

public class JobRunnerTest {

    @Test
    public void testEnumJsonSerde() {
        Map<SqlVar, String> sqlVarValueMap = Arrays.stream(new SqlVar[]{CURRENT_TIMESTAMP, CURRENT_TIME_MINUS})
                .map(sqlVar -> Pair.of(sqlVar, sqlVar.valueProvider.apply(null).toString()))
                .collect(toMap(Pair::getLeft, Pair::getRight));
        System.out.println(JsonUtil.toJsonString(sqlVarValueMap));
    }

}
