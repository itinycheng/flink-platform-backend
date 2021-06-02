package com.itiger.persona.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itiger.persona.common.enums.DataType;
import com.itiger.persona.entity.LabelParser;
import com.itiger.persona.entity.Signature;
import com.itiger.persona.enums.SqlExpression;
import com.itiger.persona.enums.SqlUdf;
import com.itiger.persona.enums.SqlVar;
import com.itiger.persona.flink.udf.common.SqlColumn;
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
import static com.itiger.persona.common.constants.Constant.UNDERSCORE;
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
import static com.itiger.persona.enums.SqlUdf.UDF_EXPRESSION;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

/**
 * @author tiny.wang
 */
@Service
@Slf4j
public class UserGroupSqlGenService {

    private static final String INSERT_OVERWRITE_EXPR = "INSERT OVERWRITE `t_hive_user_group_result` PARTITION(id = '%s', ts = %s) \n";

    private static final String INSERT_SELECT_ONE_COLUMN = "SELECT DISTINCT %s AS result_value FROM \n";

    /**
     * TODO MAP, LIST_MAP unsupported, need convert column.key to column['key']
     */
    private static final String INSERT_SELECT_MULTI_COLUMNS = "SELECT DISTINCT " + PLACEHOLDER_UDF_NAME + "(MAP[%s]) AS result_value FROM \n";

    /**
     * insert overwrite table
     */
    private static final String INSERT_OVERWRITE_STATEMENT = String.format(INSERT_OVERWRITE_EXPR, SqlVar.JOB_CODE.variable, SqlVar.CURRENT_TIMESTAMP.variable);

    private static final ThreadLocal<Set<String>> UDFS = new ThreadLocal<>();

    @Resource
    private ISignatureService iSignatureService;

    @Resource
    private HiveService hiveService;

    public String generateInsertSelect(SqlSelect sqlSelect) {
        List<String> sqls = new ArrayList<>();
        // select columns from
        String selectStatement = generateSelectStatement(sqlSelect.getSelectList());
        // join primary table and lateral table together
        String tableStatement = generateTableStatement(sqlSelect);
        String lateralTableStatement = generateLateralTableStatement(sqlSelect);
        if (StringUtils.isNotBlank(lateralTableStatement)) {
            tableStatement = String.join(COMMA, tableStatement, lateralTableStatement);
        }
        // where
        String whereStatement = generateWhereStatement(sqlSelect.getWhere());
        // add required udfs
        Optional.ofNullable(UDFS.get()).ifPresent(udfs -> {
            sqls.addAll(udfs);
            UDFS.remove();
        });
        // add full string of insert overwrite statement to sql list
        sqls.add(String.join(SPACE, INSERT_OVERWRITE_STATEMENT, selectStatement, tableStatement, whereStatement));
        return String.join(LINE_SEPARATOR, sqls);
    }

    private String generateLateralTableStatement(SqlSelect sqlSelect) {
        return sqlSelect.getWhere().exhaustiveSqlIdentifiers().stream()
                .map(identifier -> Pair.of(identifier, getSignature(identifier.getName())))
                .filter(pair -> DataType.LIST_MAP.equals(pair.getRight().getDataType()))
                .map(this::generateLateralTableSegment)
                .collect(joining(COMMA + LINE_SEPARATOR));
    }

    /**
     * only AbstractTableFunction supported
     */
    public String generateLateralTableSegment(Pair<SqlIdentifier, Signature> pair) {
        try {
            Signature signature = pair.getRight();
            SqlIdentifier identifier = pair.getLeft();
            LabelParser labelParser = signature.getLabelParser();
            String[] split = identifier.getName().split(UNDERSCORE);
            final String prefix = split[split.length - 1];
            String expression = SqlExpression.JOIN_TABLE_FUNC.expression
                    .replace(PLACEHOLDER_UDF_NAME, labelParser.getFunctionName());
            return String.format(expression, identifier.newColumnName()
                    , labelParser.getDataColumns().stream()
                            .map(sqlColumn -> String.join(UNDERSCORE, prefix, sqlColumn.name()))
                            .collect(joining(COMMA))
            );
        } catch (Exception e) {
            throw new RuntimeException("generate lateral table failed", e);
        }
    }

    public String generateSelectStatement(List<SqlIdentifier> selectList) {
        String insertSelectExpr;
        String insertSelectList;
        if (selectList.size() <= 1) {
            insertSelectExpr = INSERT_SELECT_ONE_COLUMN;
            insertSelectList = selectList.get(0).newColumnName();
        } else {
            // add udf to thread local cache
            cacheUdf(SqlUdf.TO_STRING.createStatement);
            insertSelectExpr = INSERT_SELECT_MULTI_COLUMNS.replace(PLACEHOLDER_UDF_NAME, SqlUdf.TO_STRING.name);
            insertSelectList = selectList.stream()
                    .map(SqlIdentifier::newColumnName)
                    .map(columnName -> String.join(COMMA,
                            String.join(EMPTY, SINGLE_QUOTE, columnName, SINGLE_QUOTE)
                            , String.join(EMPTY, BACK_TICK, columnName, BACK_TICK)))
                    .collect(joining(COMMA));
        }
        return String.format(insertSelectExpr, insertSelectList);
    }

    public String generateTableStatement(SqlSelect sqlSelect) {
        final Set<SqlIdentifier> allIdentifiers = getAllSqlIdentifiers(sqlSelect);
        // from table
        if (isSubQueryNeeded(allIdentifiers)) {
            return generateSubQueryStatement(allIdentifiers);
        } else {
            String accountType = allIdentifiers.stream().map(SqlIdentifier::getQualifier).findFirst()
                    .orElseThrow(() -> new RuntimeException("no sql identifier found"));
            String whichPartition = generatePartitionSegment(accountType);
            String selectList = allIdentifiers.stream().map(SqlIdentifier::toColumnAsStatement).collect(joining(COMMA));
            return String.join(SPACE, BRACKET_LEFT, SELECT, selectList,
                    FROM, sqlSelect.getFrom().getName(),
                    WHERE, whichPartition, BRACKET_RIGHT, sqlSelect.getFrom().getQualifier());
        }
    }

    public String generateWhereStatement(SqlWhere sqlWhere) {
        String where = generateWhereSegment(sqlWhere).trim();
        if (where.startsWith(BRACKET_LEFT)) {
            where = where.substring(1, where.length() - 1);
        }
        return String.join(SPACE, WHERE, where);
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

    public String generateSubQueryStatement(Set<SqlIdentifier> identifiers) {
        Map<String, List<SqlIdentifier>> grouped = identifiers.stream().collect(groupingBy(SqlIdentifier::getQualifier));
        // add uuid to each subQuery's select list
        grouped.entrySet().stream()
                .filter(entry -> entry.getValue().stream().noneMatch(identifier -> UUID.equalsIgnoreCase(identifier.getName())))
                .forEach(entry -> entry.getValue().add(SqlIdentifier.of(entry.getKey(), UUID)));
        String selectStatement = identifiers.stream().map(SqlIdentifier::toColumnAsStatement).collect(joining(COMMA));
        String tableStatement = grouped.entrySet().stream().map(entry -> String.join(SPACE, BRACKET_LEFT, SELECT,
                entry.getValue().stream().map(SqlIdentifier::toSimpleColumnStatement).collect(joining(COMMA)),
                FROM, SOURCE_TABLE_IDENTIFIER.getName(), WHERE, generatePartitionSegment(entry.getKey())
                , BRACKET_RIGHT, AS, entry.getKey()))
                .collect(joining(COMMA));
        List<String> tableAliasList = new ArrayList<>(grouped.keySet());
        String where = IntStream.range(0, tableAliasList.size() - 1)
                .mapToObj(i -> Pair.of(SqlIdentifier.of(tableAliasList.get(i), UUID), SqlIdentifier.of(tableAliasList.get(i + 1), UUID)))
                .map(pair -> String.format(SqlExpression.EQ.expression, pair.getLeft().toColumnStatement(), pair.getRight().toColumnStatement()))
                .collect(joining(SqlExpression.AND.name()));
        String subQueryStatement = String.join(SPACE, BRACKET_LEFT, SELECT, selectStatement, FROM, tableStatement, WHERE, where, BRACKET_RIGHT);
        return String.join(SPACE, subQueryStatement, SOURCE_TABLE_IDENTIFIER.getQualifier());
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
                String.join(EMPTY, DataType.STRING.quote, accountType.toUpperCase(), DataType.STRING.quote));
        return String.format(SqlExpression.AND.expression, dtStatement, accountTypeStatement);
    }

    private String generateWhereSegment(SqlWhere where) {
        if (where == null) {
            return StringUtils.EMPTY;
        } else if (where instanceof CompositeSqlWhere) {
            List<String> conditionList = new ArrayList<>();
            CompositeSqlWhere compositeCondition = (CompositeSqlWhere) where;
            SqlExpression sqlExpr = SqlExpression.of(compositeCondition.getRelation());
            for (SqlWhere condition : compositeCondition.getConditions()) {
                conditionList.add(generateWhereSegment(condition));
            }
            String joined = String.join(sqlExpr.name(), conditionList);
            return String.join(EMPTY, BRACKET_LEFT, joined, BRACKET_RIGHT);
        } else if (where instanceof SimpleSqlWhere) {
            return generateSimpleWhereSegment((SimpleSqlWhere) where);
        } else {
            throw new RuntimeException("unknown condition type");
        }
    }

    private String generateSimpleWhereSegment(SimpleSqlWhere simpleWhere) {
        SqlExpression operatorExpr = simpleWhere.getOperator();
        // fill operands to object array
        Object[] formatted = new Object[simpleWhere.getOperands().length + 1];
        Signature signature = getSignature(simpleWhere.getColumn().getName());
        formatted[0] = decorateWhereColumn(simpleWhere.getColumn(), signature);
        Pair<String, Object[]> udfAndOperands = decorateWhereOperands(simpleWhere, signature);
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

    private String decorateWhereColumn(SqlIdentifier column, Signature signature) {
        switch (signature.getDataType()) {
            case MAP:
                String[] columnAndKey = column.getName().split("\\.");
                column = SqlIdentifier.of(column.getQualifier(), columnAndKey[0]);
                String newColumnName = column.newColumnName();
                return String.join(EMPTY, newColumnName, "['", columnAndKey[1], "']");
            case LIST_MAP:
                String[] split = column.getName().split(UNDERSCORE);
                final String prefix = split[split.length - 1];
                return String.join(UNDERSCORE, prefix, column.getNames()[1]);
            case STRING:
            case NUMBER:
            case LIST:
            default:
                return column.newColumnName();
        }
    }

    private Pair<String, Object[]> decorateWhereOperands(SimpleSqlWhere simpleWhere, Signature signature) {
        String[] operands = simpleWhere.getOperands();
        SqlExpression operatorExpr = simpleWhere.getOperator();
        DataType dataType = signature.getDataType();
        // TODO check the list of sql expressions supported by sql data type
        switch (dataType) {
            case NUMBER:
            case STRING:
            case MAP:
                return Pair.of(null, Arrays.stream(operands)
                        .map(operand -> decorateWhereOperand(operatorExpr, operand, dataType))
                        .toArray());
            case LIST:
                if (operatorExpr != SqlExpression.CONTAINS) {
                    throw new RuntimeException("Currently only support sql expression `CONTAINS`");
                }
                cacheUdf(LIST_CONTAINS.createStatement);
                return Pair.of(LIST_CONTAINS.name, Arrays.stream(operands)
                        .map(operand -> decorateWhereOperand(operatorExpr, operand, DataType.STRING))
                        .toArray());
            case LIST_MAP:
                LabelParser labelParser = signature.getLabelParser();
                String createParserStatement = String.format(UDF_EXPRESSION.createStatement,
                        labelParser.getFunctionName(),
                        labelParser.getFunctionClass().getCanonicalName());
                cacheUdf(createParserStatement);
                List<SqlColumn> dataColumns = labelParser.getDataColumns();
                SqlIdentifier column = simpleWhere.getColumn();
                SqlColumn dataColumn = dataColumns.stream()
                        .filter(sqlColumn -> sqlColumn.name().equalsIgnoreCase(column.getNames()[1]))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("value at the second index of names[] not found"));
                return Pair.of(null, Arrays.stream(operands)
                        .map(operand -> decorateWhereOperand(operatorExpr, operand, dataColumn.type()))
                        .toArray());
            default:
                throw new RuntimeException(String.format("Unsupported sql data type %s", dataType.name()));
        }
    }

    private String decorateWhereOperand(SqlExpression operatorExpr, String operand, DataType dataType) {
        if (operatorExpr.isSupportMultiParameter() && !EMPTY.equals(dataType.quote)) {
            return Arrays.stream(operand.split(COMMA))
                    .map(item -> String.join(EMPTY, dataType.quote, item, dataType.quote))
                    .collect(joining(COMMA));
        } else {
            return String.join(EMPTY, dataType.quote, operand, dataType.quote);
        }
    }

    private Signature getSignature(String columnName) {
        return iSignatureService.getOne(new QueryWrapper<Signature>().lambda()
                .eq(Signature::getName, columnName));
    }

    private void cacheUdf(String udfStatement) {
        Set<String> list = UDFS.get();
        if (list == null) {
            list = new HashSet<>();
            UDFS.set(list);
        }
        list.add(udfStatement);
    }

}
