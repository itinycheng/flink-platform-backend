package com.itiger.persona.flink.udf.business;

import com.itiger.persona.common.util.JsonUtil;
import com.itiger.persona.flink.udf.entity.PositionLabel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.table.functions.ScalarFunction;

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
@Slf4j
public class PositionListParserFunction extends ScalarFunction {

    public List<String> eval(String str, String secType) {
        try {
            List<PositionLabel> positions = JsonUtil.toList(str, PositionLabel.class);
            return collectOut(positions, secType);
        } catch (Exception e) {
            log.error(">>>>> PositionListParserFunction, str:{}, secType:{}, e:", str, secType, e);
        }
        return null;
    }

    private List<String> collectOut(List<PositionLabel> positions, String secType) {
        if (CollectionUtils.isEmpty(positions)) {
            return null;
        }

        List<String> symbolList = positions.stream().map(position -> {
            String result = null;
            switch (secType) {
                case "OPT":
                    StringBuilder sb = new StringBuilder();
                    sb.append(position.getSymbol())
                            .append(" ")
                            .append(position.getExpiry())
                            .append(" ")
                            .append(position.getRight())
                            .append(" ")
                            .append(position.getStrike());
                    result = sb.toString();
                    break;
                case "FUT":
                case "STK":
                default:
                    result = position.getSymbol();
            }
            return result;
        }).filter(StringUtils::isNotBlank).collect(Collectors.toList());

        return symbolList;
    }

}
