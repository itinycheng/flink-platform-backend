package com.itiger.persona.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itiger.persona.enums.SqlExpression;
import com.itiger.persona.parser.CompositeSqlWhere;
import com.itiger.persona.parser.SimpleSqlWhere;
import com.itiger.persona.parser.SqlIdentifier;
import com.itiger.persona.parser.SqlSelect;
import com.itiger.persona.parser.SqlWhere;
import com.itiger.persona.service.SqlGenService;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

public class SqlParserTest {

    SqlSelect sqlSelect;

    @Before
    public void before() {
        SqlSelect sqlSelect = new SqlSelect();

        SimpleSqlWhere condition1 = new SimpleSqlWhere();
        condition1.setType("simple");
        condition1.setOperator(SqlExpression.EQ);
        condition1.setColumn(new SqlIdentifier("t1", "name"));
        condition1.setOperands(new String[]{"test"});

        SimpleSqlWhere subCondition1 = new SimpleSqlWhere();
        subCondition1.setType("simple");
        subCondition1.setOperator(SqlExpression.GT);
        subCondition1.setColumn(new SqlIdentifier("t1", "id"));
        subCondition1.setOperands(new String[]{"10"});

        SimpleSqlWhere subCondition2 = new SimpleSqlWhere();
        subCondition2.setType("simple");
        subCondition2.setOperator(SqlExpression.LE);
        subCondition2.setColumn(new SqlIdentifier("t1", "id"));
        subCondition2.setOperands(new String[]{"0"});

        CompositeSqlWhere condition2 = new CompositeSqlWhere();
        condition2.setType("composite");
        condition2.setRelation(SqlExpression.AND.name());
        condition2.setConditions(Arrays.asList(subCondition1, subCondition2));

        CompositeSqlWhere where = new CompositeSqlWhere();
        where.setType("composite");
        where.setRelation(SqlExpression.AND.name());
        where.setConditions(Arrays.asList(condition1, condition2));

        sqlSelect.setWhere(where);
        sqlSelect.setFrom(new SqlIdentifier("t1", "table"));
        sqlSelect.setSelectList(Arrays.asList(new SqlIdentifier("t1", "id"),
                new SqlIdentifier("t1", "name")));
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
        SqlWhere condition = new ObjectMapper().readValue(json, SqlWhere.class);
        System.out.println(condition.toString());
    }

    @Test
    public void test3() {
        String sql = new SqlGenService().generateSelect(sqlSelect);
        System.out.println(sql);
    }
}
