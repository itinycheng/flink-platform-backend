package com.itiger.persona.flink.udf.entity;

import lombok.Data;

/**
 * user label
 *
 * @author tiny.wang
 */
@Data
public class UserLabel {
    private Long sign_id;
    private String desc;
    private String value;
    private Double weight;
    private Long valid_time;
}
