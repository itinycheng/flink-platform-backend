package com.flink.platform.dao.entity.alert;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Email alert. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EmailAlert extends BaseAlert {}
