package com.itiger.persona.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itiger.persona.constants.UserGroupConst;
import com.itiger.persona.enums.SqlExpression;
import com.itiger.persona.parser.CompositeSqlWhere;
import com.itiger.persona.parser.SimpleSqlWhere;
import com.itiger.persona.parser.SqlIdentifier;
import com.itiger.persona.parser.SqlSelect;
import com.itiger.persona.parser.SqlWhere;
import com.itiger.persona.service.UserGroupSqlGenService;
import org.apache.commons.collections.ListUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class SqlParserTest {

    SqlSelect sqlSelect;

    UserGroupSqlGenService userGroupSqlGenService;

    @Before
    public void before() {
        SqlSelect sqlSelect = new SqlSelect();

        SimpleSqlWhere condition1 = new SimpleSqlWhere();
        condition1.setType("simple");
        condition1.setOperator(SqlExpression.EQ);
        condition1.setColumn(new SqlIdentifier("common", "user_type"));
        condition1.setOperands(new String[]{"3"});

        SimpleSqlWhere subCondition1 = new SimpleSqlWhere();
        subCondition1.setType("simple");
        subCondition1.setOperator(SqlExpression.GT);
        subCondition1.setColumn(new SqlIdentifier("bus", "license"));
        subCondition1.setOperands(new String[]{"TBNZ"});

        SimpleSqlWhere subCondition2 = new SimpleSqlWhere();
        subCondition2.setType("simple");
        subCondition2.setOperator(SqlExpression.LE);
        subCondition2.setColumn(new SqlIdentifier("ib", "license"));
        subCondition2.setOperands(new String[]{"TBNZ"});

        CompositeSqlWhere condition2 = new CompositeSqlWhere();
        condition2.setType("composite");
        condition2.setRelation(SqlExpression.OR.name());
        condition2.setConditions(Arrays.asList(subCondition1, subCondition2));

        CompositeSqlWhere where = new CompositeSqlWhere();
        where.setType("composite");
        where.setRelation(SqlExpression.AND.name());
        where.setConditions(Arrays.asList(condition1, condition2));

        sqlSelect.setWhere(where);
        sqlSelect.setFrom(UserGroupConst.SOURCE_TABLE_IDENTIFIER);
        sqlSelect.setSelectList(Arrays.asList(new SqlIdentifier("bus", "uuid"),
                new SqlIdentifier("common", "region")));

        this.sqlSelect = sqlSelect;
        this.userGroupSqlGenService = new UserGroupSqlGenService();
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
        String json = "{\"type\":\"simple\",\"column\":{},\"operands\":[\"name\",\"test\"]}";
        SqlWhere condition = new ObjectMapper().readValue(json, SqlWhere.class);
        System.out.println(condition.toString());
    }

    @Test
    public void test3() {
        String selectSql = userGroupSqlGenService.generateSelect(sqlSelect);
        System.out.println(selectSql);
        String insertSelect = userGroupSqlGenService.generateInsertSelect(sqlSelect);
        System.out.println(insertSelect);
    }

    @Test
    public void test4() {
        List<SqlIdentifier> selectList = sqlSelect.getSelectList();
        List<SqlIdentifier> identifiers = sqlSelect.getWhere().exhaustiveSqlIdentifiers();
        System.out.println(ListUtils.sum(selectList, identifiers));
    }

    @Test
    public void test5() {
        SqlIdentifier sqlIdentifier = userGroupSqlGenService.generateSubQueryStatement(sqlSelect);
        System.out.println(sqlIdentifier);
    }

}
