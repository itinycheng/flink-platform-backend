package com.flink.platform.udf.business;

import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.FunctionHint;
import org.apache.flink.types.Row;

import com.fasterxml.jackson.databind.type.CollectionLikeType;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.udf.common.FunctionName;
import com.flink.platform.udf.entity.PositionLabel;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * Position table function.
 *
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
 */
@FunctionName("position_table")
@FunctionHint(
        output =
                @DataTypeHint(
                        "ROW<symbol STRING, expiry STRING, strike STRING, c_right STRING, currency STRING, cost_level INTEGER, status STRING, ts BIGINT>"))
public class PositionParserFunction extends AbstractTableFunction<PositionLabel, Row> {

    private static final CollectionLikeType positionListType =
            JsonUtil.MAPPER.getTypeFactory().constructCollectionLikeType(List.class, PositionLabel.class);

    public void eval(String str) {
        List<PositionLabel> positionLabelList = JsonUtil.toList(str, positionListType);
        positionLabelList.forEach(this::collectOut);
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
        collect(Row.of(
                position.getSymbol(),
                position.getExpiry(),
                position.getStrike(),
                position.getRight(),
                position.getCurrency(),
                position.getCostLevel(),
                position.getStatus(),
                position.getTimestamp()));
    }
}
