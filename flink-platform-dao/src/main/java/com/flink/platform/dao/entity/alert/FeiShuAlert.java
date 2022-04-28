package com.flink.platform.dao.entity.alert;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Fei shu alert. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FeiShuAlert extends BaseAlert {

    private String webhook;

    private String content;
}
