package com.itiger.persona.flink.udf.business;

import com.itiger.persona.common.util.JsonUtil;
import com.itiger.persona.flink.udf.entity.PositionLabel;
import org.apache.commons.collections.CollectionUtils;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.FunctionHint;
import org.apache.flink.table.functions.TableFunction;
import org.apache.flink.types.Row;

import java.util.ArrayList;
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
@FunctionHint(output = @DataTypeHint("ROW<symbol STRING, cost_level INTEGER, strike STRING, currency STRING, expiry STRING, c_right STRING, status STRING, ts BIGINT>"))
public class PositionParserFunction extends TableFunction<Row> {

    public void eval(String str) {
        List<PositionLabel> positions = JsonUtil.toList(str, PositionLabel.class);
        collectOut(positions);

    }

    public void eval(List<String> list) {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        List<PositionLabel> positions = new ArrayList<>(list.size());
        for (String str : list) {
            positions.add(JsonUtil.toBean(str, PositionLabel.class));
        }
        collectOut(positions);
    }

    private void collectOut(List<PositionLabel> positions) {
        if (CollectionUtils.isEmpty(positions)) {
            return;
        }
        for (PositionLabel position : positions) {
            collect(Row.of(position.getSymbol(),
                    position.getCostLevel(),
                    position.getStrike(),
                    position.getCurrency(),
                    position.getExpiry(),
                    position.getRight(),
                    position.getStatus(),
                    position.getTimestamp()));
        }
    }

}
