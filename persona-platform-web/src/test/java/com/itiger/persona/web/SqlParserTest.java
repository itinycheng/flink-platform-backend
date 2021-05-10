package com.itiger.persona.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itiger.persona.enums.SqlExpression;
import com.itiger.persona.parser.CompositeCondition;
import com.itiger.persona.parser.Condition;
import com.itiger.persona.parser.SimpleCondition;
import com.itiger.persona.parser.SqlSelect;
import com.itiger.persona.service.SqlGenService;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

public class SqlParserTest {

    SqlSelect sqlSelect;

    @Before
    public void before() {
        SqlSelect sqlSelect = new SqlSelect();

        SimpleCondition condition1 = new SimpleCondition();
        condition1.setType("simple");
        condition1.setOperator(SqlExpression.EQ.name());
        condition1.setOperands(new String[]{"name", "test"});

        SimpleCondition subCondition1 = new SimpleCondition();
        subCondition1.setType("simple");
        subCondition1.setOperator(SqlExpression.GT.name());
        subCondition1.setOperands(new String[]{"id", "10"});

        SimpleCondition subCondition2 = new SimpleCondition();
        subCondition2.setType("simple");
        subCondition2.setOperator(SqlExpression.LE.name());
        subCondition2.setOperands(new String[]{"id", "0"});

        CompositeCondition condition2 = new CompositeCondition();
        condition2.setType("composite");
        condition2.setRelation(SqlExpression.AND.name());
        condition2.setConditions(Arrays.asList(subCondition1, subCondition2));

        CompositeCondition where = new CompositeCondition();
        where.setType("composite");
        where.setRelation(SqlExpression.AND.name());
        where.setConditions(Arrays.asList(condition1, condition2));

        sqlSelect.setWhere(where);
        sqlSelect.setFrom("table");
        sqlSelect.setSelectList(Arrays.asList("id", "name"));
        this.sqlSelect = sqlSelect;
    }

    @Test
    public void serdeTest() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(sqlSelect);
        System.out.println(json);

        SqlSelect sqlSelect1 = objectMapper.readValue(json, SqlSelect.class);
        System.out.println(sqlSelect1.toString());
    }

    @Test
    public void test2() throws JsonProcessingException {
        String json = "{\"type\":\"simple\",\"operator\":\"EQ\",\"operands\":[\"name\",\"test\"]}";
        Condition condition = new ObjectMapper().readValue(json, Condition.class);
        System.out.println(condition.toString());
    }

    @Test
    public void test3() {
        String sql = new SqlGenService().generateSelect(sqlSelect);
        System.out.println(sql);
    }
}
