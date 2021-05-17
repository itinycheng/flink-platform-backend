package com.itiger.persona.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itiger.persona.common.enums.SqlDataType;
import com.itiger.persona.entity.Signature;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static com.itiger.persona.common.constants.Constant.AS;
import static com.itiger.persona.common.constants.Constant.BACK_TICK;
import static com.itiger.persona.common.constants.Constant.BRACKET_LEFT;
import static com.itiger.persona.common.constants.Constant.BRACKET_RIGHT;
import static com.itiger.persona.common.constants.Constant.COMMA;
import static com.itiger.persona.common.constants.Constant.EMPTY;
import static com.itiger.persona.common.constants.Constant.LINE_SEPARATOR;
import static com.itiger.persona.common.constants.Constant.SINGLE_QUOTE;
import static com.itiger.persona.common.constants.Constant.SPACE;
import static com.itiger.persona.constants.SqlConstant.FROM;
import static com.itiger.persona.constants.SqlConstant.SELECT;
import static com.itiger.persona.constants.SqlConstant.WHERE;
import static com.itiger.persona.constants.UserGroupConst.SOURCE_TABLE_IDENTIFIER;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

/**
 * @author tiny.wang
 */
@Service
@Slf4j
public class UserGroupSqlGenService {

    private static final String UUID = "uuid";

    private static final String UDF_TO_STRING = "CREATE TEMPORARY SYSTEM FUNCTION to_string as 'com.itiger.persona.flink.udf.ToStringFunction' language java;";

    private static final String INSERT_OVERWRITE_EXPR = "INSERT OVERWRITE `t_hive_user_group_result` PARTITION(id = '%s', ts = %s) \n";

    private static final String INSERT_SELECT_ONE_COLUMN = "SELECT %s AS result FROM \n";

    private static final String INSERT_SELECT_MULTI_COLUMNS = "SELECT to_string(MAP[%s]) AS result FROM \n";

    /**
     * insert overwrite table
     */
    private static final String INSERT_OVERWRITE_STATEMENT = String.format(INSERT_OVERWRITE_EXPR, SqlVar.JOB_CODE.variable, SqlVar.CURRENT_TIMESTAMP.variable);

    @Resource
    private ISignatureService iSignatureService;

    public String generateInsertSelect(SqlSelect sqlSelect) {
        List<String> sqls = new ArrayList<>();
        // add udf
        sqls.add(UDF_TO_STRING);
        final Set<SqlIdentifier> allIdentifiers = getAllSqlIdentifiers(sqlSelect);
        final boolean subQueryExists = isSubQueryNeeded(allIdentifiers);
        // select columns from
        String selectStatement = generateSelectStatement(sqlSelect, subQueryExists);
        // table
        String tableStatement = generateTableStatement(sqlSelect, allIdentifiers, subQueryExists);
        // where
        String whereStatement = generateWhereStatement(sqlSelect, subQueryExists);
        // full string of insert overwrite statement
        sqls.add(String.join(SPACE, INSERT_OVERWRITE_STATEMENT, selectStatement, tableStatement, whereStatement));
        return String.join(LINE_SEPARATOR, sqls);
    }

    public String generateSelectStatement(SqlSelect sqlSelect, boolean subQueryExists) {
        String insertSelectExpr;
        String insertSelectList;
        if (sqlSelect.getSelectList().size() <= 1) {
            insertSelectExpr = INSERT_SELECT_ONE_COLUMN;
            insertSelectList = sqlSelect.getSelectList().get(0).correctColumnName(subQueryExists);
        } else {
            insertSelectExpr = INSERT_SELECT_MULTI_COLUMNS;
            insertSelectList = sqlSelect.getSelectList().stream()
                    .map(identifier -> identifier.correctColumnName(subQueryExists))
                    .map(columnName -> String.join(COMMA,
                            String.join(EMPTY, SINGLE_QUOTE, columnName, SINGLE_QUOTE)
                            , String.join(EMPTY, BACK_TICK, columnName, BACK_TICK)))
                    .collect(joining(COMMA));
        }
        return String.format(insertSelectExpr, insertSelectList);
    }

    public String generateTableStatement(SqlSelect sqlSelect, Set<SqlIdentifier> identifiers, boolean subQueryExists) {
        // from table
        if (subQueryExists) {
            return generateSubQueryStatement(identifiers).toTableString();
        } else {
            return sqlSelect.getFrom().toTableString();
        }
    }

    public String generateWhereStatement(SqlSelect sqlSelect, boolean subQueryExists) {
        String where = generateWhereStatement(sqlSelect.getWhere(), subQueryExists);
        if (StringUtils.isNotBlank(where)) {
            where = where.trim();
            where = String.join(SPACE, WHERE, where.substring(1, where.length() - 1));
        }
        return where;
    }

    private Set<SqlIdentifier> getAllSqlIdentifiers(SqlSelect sqlSelect) {
        List<SqlIdentifier> selectList = sqlSelect.getSelectList();
        List<SqlIdentifier> whereList = sqlSelect.getWhere().exhaustiveSqlIdentifiers();
        Set<SqlIdentifier> identifiers = new HashSet<>();
        identifiers.addAll(selectList);
        identifiers.addAll(whereList);
        return identifiers;
    }

    private boolean isSubQueryNeeded(Set<SqlIdentifier> identifiers) {
        return identifiers.stream().map(SqlIdentifier::getQualifier).distinct().count() > 1;
    }

    public SqlIdentifier generateSubQueryStatement(Set<SqlIdentifier> identifiers) {
        Map<String, List<SqlIdentifier>> grouped = identifiers.stream().collect(groupingBy(SqlIdentifier::getQualifier));
        String selectStatement = identifiers.stream().map(SqlIdentifier::toColumnAsStatement).collect(joining(COMMA));
        String tableStatement = grouped.entrySet().stream().map(entry -> String.join(SPACE, BRACKET_LEFT, SELECT,
                entry.getValue().stream().map(SqlIdentifier::toSimpleColumnStatement).collect(joining(COMMA)),
                FROM, SOURCE_TABLE_IDENTIFIER.getName(), BRACKET_RIGHT, AS, entry.getKey()))
                .collect(joining(COMMA));
        List<String> tableAliasList = new ArrayList<>(grouped.keySet());
        String where = IntStream.range(0, tableAliasList.size() - 1)
                .mapToObj(i -> Pair.of(new SqlIdentifier(tableAliasList.get(i), UUID), new SqlIdentifier(tableAliasList.get(i + 1), UUID)))
                .map(pair -> String.format(SqlExpression.EQ.expression, pair.getLeft().toColumnStatement(), pair.getRight().toColumnStatement()))
                .collect(joining(SqlExpression.AND.name()));
        String subQueryStatement = String.join(SPACE, BRACKET_LEFT, SELECT, selectStatement, FROM, tableStatement, WHERE, where, BRACKET_RIGHT);
        return new SqlIdentifier(SOURCE_TABLE_IDENTIFIER.getQualifier(), subQueryStatement);
    }

    private String generateWhereStatement(SqlWhere where, boolean subQueryExists) {
        if (where == null) {
            return StringUtils.EMPTY;
        } else if (where instanceof CompositeSqlWhere) {
            List<String> conditionList = new ArrayList<>();
            CompositeSqlWhere compositeCondition = (CompositeSqlWhere) where;
            SqlExpression sqlExpr = SqlExpression.of(compositeCondition.getRelation());
            for (SqlWhere condition : compositeCondition.getConditions()) {
                conditionList.add(generateWhereStatement(condition, subQueryExists));
            }
            String joined = String.join(sqlExpr.name(), conditionList);
            return String.join(EMPTY, SPACE, BRACKET_LEFT, joined, BRACKET_RIGHT, SPACE);
        } else if (where instanceof SimpleSqlWhere) {
            return generateWhereSegment((SimpleSqlWhere) where, subQueryExists);
        } else {
            throw new RuntimeException("unknown condition type");
        }
    }

    private String generateWhereSegment(SimpleSqlWhere simpleCondition, boolean subQueryExists) {
        SqlExpression operatorExpr = simpleCondition.getOperator();
        String columnName = simpleCondition.getOperands()[0];
        Signature signature = iSignatureService.getOne(new QueryWrapper<Signature>().lambda()
                .eq(Signature::getName, columnName));
        SqlDataType sqlDataType = signature.getDataType();
        String[] operands = simpleCondition.getOperands();
        Object[] formatted = new Object[operands.length + 1];
        String columnSegment;
        if (subQueryExists) {
            columnSegment = simpleCondition.getColumn().newColumnName();
        } else {
            columnSegment = simpleCondition.getColumn().toSimpleColumnStatement();
        }
        formatted[0] = columnSegment;
        for (int i = 0; i < operands.length; i++) {
            formatted[i + 1] = decorateOperand(operatorExpr, operands[i], sqlDataType);
        }
        return String.format(operatorExpr.expression, formatted);
    }

    private String decorateOperand(SqlExpression operatorExpr, String operand, SqlDataType sqlDataType) {
        if (operatorExpr.isSupportMultiParameter() && !EMPTY.equals(sqlDataType.quote)) {
            return Arrays.stream(operand.split(COMMA))
                    .map(item -> String.join(EMPTY, sqlDataType.quote, item, sqlDataType.quote))
                    .collect(joining(COMMA));
        } else {
            return String.join(EMPTY, sqlDataType.quote, operand, sqlDataType.quote);
        }
    }

}
