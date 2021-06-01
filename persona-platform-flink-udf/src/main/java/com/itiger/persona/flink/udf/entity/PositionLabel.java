package com.itiger.persona.flink.udf.entity;

import com.itiger.persona.common.enums.SqlDataType;
import com.itiger.persona.flink.udf.common.SqlColumn;
import lombok.Data;

/**
 * @author tiny.wang
 */
@Data
public class PositionLabel {

    @SqlColumn(priority = 1, name = "symbol", type = SqlDataType.STRING)
    private String symbol;

    @SqlColumn(priority = 2, name = "expiry", type = SqlDataType.STRING)
    private String expiry;

    @SqlColumn(priority = 3, name = "strike", type = SqlDataType.STRING)
    private String strike;

    @SqlColumn(priority = 4, name = "c_right", type = SqlDataType.STRING)
    private String right;

    @SqlColumn(priority = 5, name = "currency", type = SqlDataType.STRING)
    private String currency;

    @SqlColumn(priority = 6, name = "cost_level", type = SqlDataType.INT)
    private Integer costLevel;

    @SqlColumn(priority = 7, name = "status", type = SqlDataType.STRING)
    private String status;

    @SqlColumn(priority = 8, name = "ts", type = SqlDataType.LONG)
    private Long timestamp;

}

