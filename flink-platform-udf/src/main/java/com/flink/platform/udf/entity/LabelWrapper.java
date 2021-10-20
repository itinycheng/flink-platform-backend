package com.flink.platform.udf.entity;

import lombok.Data;

/** User label. */
@Data
public class LabelWrapper {

    private Long signId;

    private String desc;

    private Object value;

    private Double weight;

    private Long validTime;
}
