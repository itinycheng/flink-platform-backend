package com.itiger.persona.flink.udf.business;

import com.itiger.persona.common.util.JsonUtil;
import com.itiger.persona.common.util.Preconditions;
import com.itiger.persona.flink.udf.entity.PositionLabel;
import com.itiger.persona.flink.udf.util.UdfUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.FunctionHint;
import org.apache.flink.table.functions.TableFunction;
import org.apache.flink.types.Row;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
@FunctionHint(output = @DataTypeHint("ROW<symbol STRING, expiry STRING, strike STRING, c_right STRING, currency STRING, cost_level INTEGER, status STRING, ts BIGINT>"))
public class PositionParserFunction extends TableFunction<Row> {

    public PositionParserFunction() {
        // validate columns' definition
        String tableDescription = this.getClass().getAnnotation(FunctionHint.class)
                .output().value();
        String expectDescription = UdfUtil.extractSqlColumnAnnotation(PositionLabel.class)
                .stream().map(sqlColumn -> String.join(" ", sqlColumn.name(), sqlColumn.type().sqlType))
                .collect(Collectors.joining(", ", "ROW<", ">"));
        Preconditions.checkThrow(!tableDescription.equalsIgnoreCase(expectDescription),
                () -> new RuntimeException("value of @DataTypeHint isn't correct"));
    }

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
                    position.getExpiry(),
                    position.getStrike(),
                    position.getRight(),
                    position.getCurrency(),
                    position.getCostLevel(),
                    position.getStatus(),
                    position.getTimestamp()));
        }
    }

}
