package com.itiger.persona.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itiger.persona.entity.Signature;
import com.itiger.persona.enums.SqlDataType;
import com.itiger.persona.enums.SqlExpression;
import com.itiger.persona.enums.SqlVar;
import com.itiger.persona.parser.CompositeSqlWhere;
import com.itiger.persona.parser.SimpleSqlWhere;
import com.itiger.persona.parser.SqlIdentifier;
import com.itiger.persona.parser.SqlSelect;
import com.itiger.persona.parser.SqlWhere;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static com.itiger.persona.constants.SqlConstant.AS;
import static com.itiger.persona.constants.SqlConstant.BRACKET_LEFT;
import static com.itiger.persona.constants.SqlConstant.BRACKET_RIGHT;
import static com.itiger.persona.constants.SqlConstant.COMMA;
import static com.itiger.persona.constants.SqlConstant.DOT;
import static com.itiger.persona.constants.SqlConstant.EMPTY;
import static com.itiger.persona.constants.SqlConstant.FROM;
import static com.itiger.persona.constants.SqlConstant.LINE_SEPARATOR;
import static com.itiger.persona.constants.SqlConstant.SELECT;
import static com.itiger.persona.constants.SqlConstant.SEMICOLON;
import static com.itiger.persona.constants.SqlConstant.SPACE;
import static com.itiger.persona.constants.SqlConstant.WHERE;
import static com.itiger.persona.constants.UserGroupConst.SOURCE_TABLE_IDENTIFIER;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * @author tiny.wang
 */
@Service
@Slf4j
public class UserGroupSqlGenService {

    private static final String UUID = "uuid";

    private static final String T = "t";

    private static final String UDF_TO_STRING = "CREATE TEMPORARY SYSTEM FUNCTION to_string as 'com.itiger.persona.flink.udf.ToStringFunction' language java;";

    private static final String INSERT_PREFIX = "INSERT OVERWRITE `t_hive_user_group_result` PARTITION(id = %s, ts = %s) \n";

    private static final String INSERT_SELECT_ONE_COLUMN = "SELECT %s AS result FROM \n";

    private static final String INSERT_SELECT_MULTI_COLUMNS = "SELECT to_string(MAP[%s]) AS result FROM \n";

    @Resource
    private ISignatureService iSignatureService;

    public String generateInsertSelect(SqlSelect sqlSelect) {
        List<String> sqls = new ArrayList<>();
        // insert overwrite table
        String insertPrefix = String.format(INSERT_PREFIX, SqlVar.JOB_CODE.variable, SqlVar.CURRENT_TIMESTAMP.variable);

        // select columns from
        String insertSelectExpr;
        String insertSelectList;
        if (sqlSelect.getSelectList().size() <= 1) {
            insertSelectExpr = INSERT_SELECT_ONE_COLUMN;
            insertSelectList = String.join(DOT, T, sqlSelect.getSelectList().get(0).getName());
        } else {
            // add udf
            sqls.add(UDF_TO_STRING);
            insertSelectExpr = INSERT_SELECT_MULTI_COLUMNS;
            insertSelectList = sqlSelect.getSelectList().stream()
                    .map(identifier -> String.join(COMMA,
                            SqlDataType.STRING.prefix + identifier.getName() + SqlDataType.STRING.suffix
                            , String.join(DOT, T, identifier.getName())))
                    .collect(joining(COMMA));
        }
        String insertSelect = String.format(insertSelectExpr, insertSelectList);

        // table
        String insertTable = generateSelect(sqlSelect);

        // full string of insert overwrite statement
        sqls.add(String.join(EMPTY, insertPrefix, insertSelect, BRACKET_LEFT, insertTable, BRACKET_RIGHT, T));
        return String.join(LINE_SEPARATOR, sqls);
    }

    public String generateSelect(SqlSelect sqlSelect) {
        // select columns
        List<String> columns = sqlSelect.getSelectList().stream()
                .map(SqlIdentifier::toColumnString).collect(toList());
        String select = String.join(COMMA, columns);
        // from table
        String from = sqlSelect.getFrom().toTableString();
        // where statement
        String where = generateWhereStatement(sqlSelect.getWhere());
        if (StringUtils.isNotBlank(where)) {
            where = where.trim();
            where = String.join(SPACE, WHERE, where.substring(1, where.length() - 1));
        }
        return String.join(SPACE, SELECT, select, FROM, from, where, SEMICOLON);
    }

    public SqlIdentifier generateSubQueryStatement(SqlSelect sqlSelect) {
        List<SqlIdentifier> selectList = sqlSelect.getSelectList();
        List<SqlIdentifier> whereList = sqlSelect.getWhere().exhaustiveSqlIdentifiers();
        Set<SqlIdentifier> identifiers = new HashSet<>();
        identifiers.addAll(selectList);
        identifiers.addAll(whereList);
        Map<String, List<SqlIdentifier>> grouped = identifiers.stream().collect(groupingBy(SqlIdentifier::getQualifier));
        if (grouped.size() <= 1) {
            return sqlSelect.getFrom();
        }
        String tableStatement = grouped.entrySet().stream().map(entry -> String.join(SPACE, BRACKET_LEFT, SELECT,
                entry.getValue().stream().map(SqlIdentifier::toSimpleColumnStatement).collect(joining(COMMA)),
                FROM, SOURCE_TABLE_IDENTIFIER.getName(), BRACKET_RIGHT, AS, entry.getKey()))
                .collect(joining(COMMA));
        List<String> tableAliasList = new ArrayList<>(grouped.keySet());
        String where = IntStream.range(0, tableAliasList.size() - 1)
                .mapToObj(i -> Pair.of(new SqlIdentifier(tableAliasList.get(i), UUID), new SqlIdentifier(tableAliasList.get(i + 1), UUID)))
                .map(pair -> String.format(SqlExpression.EQ.expression, pair.getLeft().toColumnString(), pair.getRight().toColumnString()))
                .collect(joining(SqlExpression.AND.name()));
        String subQueryStatement = String.join(SPACE, BRACKET_LEFT, tableStatement, WHERE, where, BRACKET_RIGHT);
        return new SqlIdentifier(SOURCE_TABLE_IDENTIFIER.getQualifier(), subQueryStatement);
    }

    private String generateWhereStatement(SqlWhere where) {
        if (where == null) {
            return StringUtils.EMPTY;
        } else if (where instanceof CompositeSqlWhere) {
            List<String> conditionList = new ArrayList<>();
            CompositeSqlWhere compositeCondition = (CompositeSqlWhere) where;
            SqlExpression sqlExpr = SqlExpression.of(compositeCondition.getRelation());
            for (SqlWhere condition : compositeCondition.getConditions()) {
                conditionList.add(generateWhereStatement(condition));
            }
            String joined = String.join(sqlExpr.name(), conditionList);
            return String.join(EMPTY, SPACE, BRACKET_LEFT, joined, BRACKET_RIGHT, SPACE);
        } else if (where instanceof SimpleSqlWhere) {
            return generateWhereSegment((SimpleSqlWhere) where);
        } else {
            throw new RuntimeException("unknown condition type");
        }
    }

    private String generateWhereSegment(SimpleSqlWhere simpleCondition) {
        SqlExpression operatorExpr = simpleCondition.getOperator();
        String columnName = simpleCondition.getOperands()[0];
        Signature signature = iSignatureService.getOne(new QueryWrapper<Signature>().lambda()
                .eq(Signature::getName, columnName));
        SqlDataType sqlDataType = signature.getDataType();
        String[] operands = simpleCondition.getOperands();
        Object[] formatted = new Object[operands.length + 1];
        formatted[0] = simpleCondition.getColumn().toColumnString();
        for (int i = 0; i < operands.length; i++) {
            formatted[i + 1] = String.join(EMPTY, sqlDataType.prefix, operands[i], sqlDataType.suffix);
        }
        return String.format(operatorExpr.expression, formatted);
    }

}
