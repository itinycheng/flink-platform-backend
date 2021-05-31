package com.itiger.persona.flink.udf.entity;

import lombok.Data;

/**
 * user label
 *
 * @author tiny.wang
 */
@Data
public class LabelWrapper {

    private Long sign_id;

    private String desc;

    private Object value;

    private Double weight;

    private Long valid_time;
}
