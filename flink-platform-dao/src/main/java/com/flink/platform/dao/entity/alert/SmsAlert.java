package com.flink.platform.dao.entity.alert;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Sms alert. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SmsAlert extends BaseAlert {}
