package com.flink.platform.udf.entity;

import com.flink.platform.common.enums.DataType;
import com.flink.platform.udf.common.SqlColumn;
import lombok.Data;

/** Position label. */
@Data
public class PositionLabel {

    @SqlColumn(priority = 1, name = "symbol", type = DataType.STRING)
    private String symbol;

    @SqlColumn(priority = 2, name = "expiry", type = DataType.STRING)
    private String expiry;

    @SqlColumn(priority = 3, name = "strike", type = DataType.STRING)
    private String strike;

    @SqlColumn(priority = 4, name = "c_right", type = DataType.STRING)
    private String right;

    @SqlColumn(priority = 5, name = "currency", type = DataType.STRING)
    private String currency;

    @SqlColumn(priority = 6, name = "cost_level", type = DataType.INT)
    private Integer costLevel;

    @SqlColumn(priority = 7, name = "status", type = DataType.STRING)
    private String status;

    @SqlColumn(priority = 8, name = "ts", type = DataType.LONG)
    private Long timestamp;
}
