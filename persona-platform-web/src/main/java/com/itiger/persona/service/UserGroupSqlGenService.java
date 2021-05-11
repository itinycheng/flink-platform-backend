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
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * @author tiny.wang
 */
@Service
@Slf4j
public class UserGroupSqlGenService {

    private static final String T = "t";

    @Resource
    private ISignatureService iSignatureService;

    public String generateInsertSelect(SqlSelect sqlSelect) {
        String insertPrefix = String.format("INSERT OVERWRITE `t_hive_user_group_result` PARTITION(id = %s, ts = %s) \n",
                SqlVar.JOB_CODE.variable, SqlVar.CURRENT_TIMESTAMP.variable);
        String joinedSelectList = sqlSelect.getSelectList().stream()
                .map(identifier -> String.join(", ",
                        SqlDataType.STRING.prefix + identifier.getName() + SqlDataType.STRING.suffix
                        , String.join(".", T, identifier.getName())))
                .collect(Collectors.joining(", "));
        String insertSelect = String.format("SELECT to_json(MAP[%s]) AS result FROM \n", joinedSelectList);
        String insertTable = generateSelect(sqlSelect);
        return String.join("", insertPrefix, insertSelect, "( ", insertTable, ") ", T);
    }

    public String generateSelect(SqlSelect sqlSelect) {
        // select columns
        List<String> columns = sqlSelect.getSelectList().stream()
                .map(SqlIdentifier::toColumnString).collect(toList());
        String select = String.join(", ", columns);
        // from table
        String from = sqlSelect.getFrom().toTableString();
        // where statement
        String where = generateWhereStatement(sqlSelect.getWhere());
        if (StringUtils.isNotBlank(where)) {
            where = where.trim();
            where = String.join(" ", "WHERE", where.substring(1, where.length() - 1));
        }
        return String.join(" ", "SELECT", select, "FROM", from, where);
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
            return String.join("", " (", joined, ") ");
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
            formatted[i + 1] = String.join("", sqlDataType.prefix, operands[i], sqlDataType.suffix);
        }
        return String.format(operatorExpr.expression, formatted);
    }

}
