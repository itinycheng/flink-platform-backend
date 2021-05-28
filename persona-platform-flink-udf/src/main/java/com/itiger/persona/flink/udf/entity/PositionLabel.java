package com.itiger.persona.flink.udf.entity;

import lombok.Data;

/**
 * @author tiny.wang
 */
@Data
public class PositionLabel {

    private String symbol;

    private String expiry;

    private String strike;

    private String right;

    private String currency;

    private Integer costLevel;

    private String status;

    private Long timestamp;

}

