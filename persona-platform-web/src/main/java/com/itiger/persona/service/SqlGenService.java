package com.itiger.persona.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itiger.persona.entity.Signature;
import com.itiger.persona.enums.SqlDataType;
import com.itiger.persona.enums.SqlExpression;
import com.itiger.persona.parser.CompositeCondition;
import com.itiger.persona.parser.Condition;
import com.itiger.persona.parser.SimpleCondition;
import com.itiger.persona.parser.SqlSelect;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tiny.wang
 */
@Service
@Slf4j
public class SqlGenService {

    @Autowired
    private ISignatureService iSignatureService;

    public String generateSelect(SqlSelect sqlSelect) {
        String select = String.join(",", sqlSelect.getSelectList());
        String from = sqlSelect.getFrom();
        String where = generateWhereStatement(sqlSelect.getWhere());
        if (StringUtils.isNotBlank(where)) {
            where = String.join(" ", "WHERE", where.substring(1, where.length() - 1));
        }
        return String.join(" ", "SELECT", select, "FROM", from, where);
    }

    private String generateWhereStatement(Condition where) {
        if (where == null) {
            return StringUtils.EMPTY;
        } else if (where instanceof CompositeCondition) {
            List<String> conditionList = new ArrayList<>();
            CompositeCondition compositeCondition = (CompositeCondition) where;
            SqlExpression sqlExpr = SqlExpression.of(compositeCondition.getRelation());
            for (Condition condition : compositeCondition.getConditions()) {
                conditionList.add(generateWhereStatement(condition));
            }
            String joined = String.join(sqlExpr.name(), conditionList);
            return String.join("", " (", joined, ") ");
        } else if (where instanceof SimpleCondition) {
            return generateWhereSegment((SimpleCondition) where);
        } else {
            throw new RuntimeException("unknown condition type");
        }
    }

    private String generateWhereSegment(SimpleCondition simpleCondition) {
        SqlExpression sqlExpr = SqlExpression.of(simpleCondition.getOperator());
        String columnName = simpleCondition.getOperands()[0];
        Signature signature = iSignatureService.getOne(new QueryWrapper<Signature>().lambda()
                .eq(Signature::getName, columnName));
        SqlDataType sqlDataType = signature.getDataType();
        String[] operands = simpleCondition.getOperands();
        Object[] formatted = new Object[operands.length];
        for (int i = 0; i < operands.length; i++) {
            if (i == 0) {
                formatted[i] = operands[i];
            } else {
                formatted[i] = String.join("", sqlDataType.prefix, operands[i], sqlDataType.suffix);
            }
        }
        return String.format(sqlExpr.expression, formatted);
    }

}
