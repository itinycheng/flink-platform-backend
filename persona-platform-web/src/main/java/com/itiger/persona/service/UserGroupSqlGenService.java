package com.itiger.persona.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itiger.persona.common.enums.SqlDataType;
import com.itiger.persona.entity.Signature;
import com.itiger.persona.enums.SqlExpression;
import com.itiger.persona.enums.SqlUdf;
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
import java.util.Optional;
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
import static com.itiger.persona.common.constants.Constant.SLASH;
import static com.itiger.persona.common.constants.Constant.SPACE;
import static com.itiger.persona.constants.SqlConstant.FROM;
import static com.itiger.persona.constants.SqlConstant.SELECT;
import static com.itiger.persona.constants.SqlConstant.WHERE;
import static com.itiger.persona.constants.UserGroupConst.DT_PARTITION_PREFIX;
import static com.itiger.persona.constants.UserGroupConst.PLACEHOLDER_UDF_NAME;
import static com.itiger.persona.constants.UserGroupConst.QUERY_SOURCE_TABLE_PARTITIONS;
import static com.itiger.persona.constants.UserGroupConst.SOURCE_TABLE_ACCOUNT_TYPE_PARTITION;
import static com.itiger.persona.constants.UserGroupConst.SOURCE_TABLE_DT_PARTITION;
import static com.itiger.persona.constants.UserGroupConst.SOURCE_TABLE_IDENTIFIER;
import static com.itiger.persona.constants.UserGroupConst.UUID;
import static com.itiger.persona.enums.SqlUdf.LIST_CONTAINS;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

/**
 * @author tiny.wang
 */
@Service
@Slf4j
public class UserGroupSqlGenService {

    private static final String INSERT_OVERWRITE_EXPR = "INSERT OVERWRITE `t_hive_user_group_result` PARTITION(id = '%s', ts = %s) \n";

    private static final String INSERT_SELECT_ONE_COLUMN = "SELECT %s AS result FROM \n";

    private static final String INSERT_SELECT_MULTI_COLUMNS = "SELECT " + PLACEHOLDER_UDF_NAME + "(MAP[%s]) AS result FROM \n";

    /**
     * insert overwrite table
     */
    private static final String INSERT_OVERWRITE_STATEMENT = String.format(INSERT_OVERWRITE_EXPR, SqlVar.JOB_CODE.variable, SqlVar.CURRENT_TIMESTAMP.variable);

    private static final ThreadLocal<List<String>> UDFS = new ThreadLocal<>();

    @Resource
    private ISignatureService iSignatureService;

    @Resource
    private HiveService hiveService;

    public String generateInsertSelect(SqlSelect sqlSelect) {
        List<String> sqls = new ArrayList<>();
        final Set<SqlIdentifier> allIdentifiers = getAllSqlIdentifiers(sqlSelect);
        final boolean subQueryExists = isSubQueryNeeded(allIdentifiers);
        // select columns from
        String selectStatement = generateSelectStatement(sqlSelect, subQueryExists);
        // table
        String tableStatement = generateTableStatement(sqlSelect, allIdentifiers, subQueryExists);
        // where
        String whereStatement = generateWhereStatement(sqlSelect, subQueryExists);
        // add required udfs
        Optional.ofNullable(UDFS.get()).ifPresent(udfs -> {
            sqls.addAll(udfs);
            UDFS.remove();
        });
        // add full string of insert overwrite statement to sql list
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
            // add udf to thread local cache
            cacheUdf(SqlUdf.TO_STRING.createStatement);
            insertSelectExpr = INSERT_SELECT_MULTI_COLUMNS.replace(PLACEHOLDER_UDF_NAME, SqlUdf.TO_STRING.name);
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
        String where = generateWhereSegment(sqlSelect.getWhere(), subQueryExists);
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
                FROM, SOURCE_TABLE_IDENTIFIER.getName(), WHERE, generatePartitionSegment(entry.getKey())
                , BRACKET_RIGHT, AS, entry.getKey()))
                .collect(joining(COMMA));
        List<String> tableAliasList = new ArrayList<>(grouped.keySet());
        String where = IntStream.range(0, tableAliasList.size() - 1)
                .mapToObj(i -> Pair.of(new SqlIdentifier(tableAliasList.get(i), UUID), new SqlIdentifier(tableAliasList.get(i + 1), UUID)))
                .map(pair -> String.format(SqlExpression.EQ.expression, pair.getLeft().toColumnStatement(), pair.getRight().toColumnStatement()))
                .collect(joining(SqlExpression.AND.name()));
        String subQueryStatement = String.join(SPACE, BRACKET_LEFT, SELECT, selectStatement, FROM, tableStatement, WHERE, where, BRACKET_RIGHT);
        return new SqlIdentifier(SOURCE_TABLE_IDENTIFIER.getQualifier(), subQueryStatement);
    }

    private String generatePartitionSegment(String accountType) {
        List<String> partitions = hiveService.getList(QUERY_SOURCE_TABLE_PARTITIONS);
        long maxDt = partitions.stream()
                .map(partition -> Arrays.stream(partition.split(SLASH))
                        .filter(p -> p.startsWith(DT_PARTITION_PREFIX))
                        .map(pa -> pa.replace(DT_PARTITION_PREFIX, EMPTY))
                        .map(Long::parseLong)
                        .findFirst().orElseThrow(() -> new RuntimeException("can not parse partition: dt")))
                .mapToLong(ts -> ts)
                .max()
                .orElseThrow(() -> new RuntimeException("maximum dt not found"));
        String dtStatement = String.format(SqlExpression.EQ.expression, SOURCE_TABLE_DT_PARTITION, maxDt);
        String accountTypeStatement = String.format(SqlExpression.EQ.expression, SOURCE_TABLE_ACCOUNT_TYPE_PARTITION,
                String.join(EMPTY, SqlDataType.STRING.quote, accountType.toUpperCase(), SqlDataType.STRING.quote));
        return String.format(SqlExpression.AND.expression, dtStatement, accountTypeStatement);
    }

    private String generateWhereSegment(SqlWhere where, boolean subQueryExists) {
        if (where == null) {
            return StringUtils.EMPTY;
        } else if (where instanceof CompositeSqlWhere) {
            List<String> conditionList = new ArrayList<>();
            CompositeSqlWhere compositeCondition = (CompositeSqlWhere) where;
            SqlExpression sqlExpr = SqlExpression.of(compositeCondition.getRelation());
            for (SqlWhere condition : compositeCondition.getConditions()) {
                conditionList.add(generateWhereSegment(condition, subQueryExists));
            }
            String joined = String.join(sqlExpr.name(), conditionList);
            return String.join(EMPTY, SPACE, BRACKET_LEFT, joined, BRACKET_RIGHT, SPACE);
        } else if (where instanceof SimpleSqlWhere) {
            return generateSimpleWhereSegment((SimpleSqlWhere) where, subQueryExists);
        } else {
            throw new RuntimeException("unknown condition type");
        }
    }

    private String generateSimpleWhereSegment(SimpleSqlWhere simpleWhere, boolean subQueryExists) {
        SqlExpression operatorExpr = simpleWhere.getOperator();
        // get column name
        SqlIdentifier column = simpleWhere.getColumn();
        String favorableColumnName = subQueryExists ? column.newColumnName() : column.toSimpleColumnStatement();
        // fill operands to object array
        Object[] formatted = new Object[simpleWhere.getOperands().length + 1];
        formatted[0] = favorableColumnName;
        Pair<String, Object[]> udfAndOperands = decorateWhereOperands(simpleWhere);
        // replace variable in sql expression
        String udfName = udfAndOperands.getLeft();
        String expression = operatorExpr.expression;
        if (StringUtils.isNotBlank(udfName)) {
            expression = expression.replace(PLACEHOLDER_UDF_NAME, udfName);
        }
        // combine operands
        Object[] decoratedOperands = udfAndOperands.getRight();
        System.arraycopy(decoratedOperands, 0, formatted, 1, decoratedOperands.length);
        return String.format(expression, formatted);
    }

    private Pair<String, Object[]> decorateWhereOperands(SimpleSqlWhere simpleWhere) {
        String[] operands = simpleWhere.getOperands();
        SqlExpression operatorExpr = simpleWhere.getOperator();
        String columnName = simpleWhere.getColumn().getName();
        Signature signature = iSignatureService.getOne(new QueryWrapper<Signature>().lambda()
                .eq(Signature::getName, columnName));
        SqlDataType sqlDataType = signature.getDataType();
        //TODO check the list of sql expressions supported by sql data type
        switch (sqlDataType) {
            case NUMBER:
            case STRING:
                Object[] simpleTypeOperands = Arrays.stream(operands)
                        .map(operand -> decorateWhereOperand(operatorExpr, operand, sqlDataType))
                        .toArray();
                return Pair.of(null, simpleTypeOperands);
            case LIST:
                if (operatorExpr != SqlExpression.CONTAINS) {
                    throw new RuntimeException("Currently support sql expression `CONTAINS`");
                }
                cacheUdf(LIST_CONTAINS.createStatement);
                Object[] listTypeOperands = Arrays.stream(operands)
                        .map(operand -> decorateWhereOperand(operatorExpr, operand, SqlDataType.STRING))
                        .toArray();
                return Pair.of(LIST_CONTAINS.name, listTypeOperands);
            case MAP:
            case LIST_MAP:
            default:
                throw new RuntimeException(String.format("Unsupported sql data type %s", sqlDataType.name()));
        }
    }

    private String decorateWhereOperand(SqlExpression operatorExpr, String operand, SqlDataType sqlDataType) {
        if (operatorExpr.isSupportMultiParameter() && !EMPTY.equals(sqlDataType.quote)) {
            return Arrays.stream(operand.split(COMMA))
                    .map(item -> String.join(EMPTY, sqlDataType.quote, item, sqlDataType.quote))
                    .collect(joining(COMMA));
        } else {
            return String.join(EMPTY, sqlDataType.quote, operand, sqlDataType.quote);
        }
    }

    private void cacheUdf(String udfStatement) {
        List<String> list = UDFS.get();
        if (list == null) {
            list = new ArrayList<>();
            UDFS.set(list);
        }
        list.add(udfStatement);
    }

}
