package com.itiger.persona.flink.udf.business;

import com.itiger.persona.common.util.JsonUtil;
import com.itiger.persona.flink.udf.common.FunctionName;
import com.itiger.persona.flink.udf.entity.PositionLabel;
import org.apache.commons.collections.CollectionUtils;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.FunctionHint;
import org.apache.flink.types.Row;

import java.util.List;

/**
 * <pre>
 * [
 *     {
 *         "symbol": "BILI",
 *         "costLevel": 1,
 *         "strike": "",
 *         "currency": "USD",
 *         "expiry": "",
 *         "right": "",
 *         "status": "LONG",
 *         "timestamp": 1621990862104
 *     },
 *     {
 *         "symbol": "BIDU",
 *         "costLevel": 1,
 *         "strike": "",
 *         "currency": "USD",
 *         "expiry": "",
 *         "right": "",
 *         "status": "LONG",
 *         "timestamp": 1621990862101
 *     }
 * ]
 * </pre>
 *
 * @author tiny.wang
 */
@FunctionName("position_table")
@FunctionHint(output = @DataTypeHint("ROW<symbol STRING, expiry STRING, strike STRING, c_right STRING, currency STRING, cost_level INTEGER, status STRING, ts BIGINT>"))
public class PositionParserFunction extends AbstractTableFunction<PositionLabel, Row> {

    public void eval(String str) {
        JsonUtil.toList(str, PositionLabel.class)
                .forEach(this::collectOut);
    }

    public void eval(List<String> list) {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        for (String str : list) {
            collectOut(JsonUtil.toBean(str, PositionLabel.class));
        }
    }

    private void collectOut(PositionLabel position) {
        if (position == null) {
            return;
        }
        collect(Row.of(position.getSymbol(),
                position.getExpiry(),
                position.getStrike(),
                position.getRight(),
                position.getCurrency(),
                position.getCostLevel(),
                position.getStatus(),
                position.getTimestamp()));
    }

}
