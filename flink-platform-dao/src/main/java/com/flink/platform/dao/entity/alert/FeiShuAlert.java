package com.flink.platform.dao.entity.alert;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Fei shu alert. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FeiShuAlert extends BaseAlert {

    private String webhook;

    private Map<String, Object> content;
}
