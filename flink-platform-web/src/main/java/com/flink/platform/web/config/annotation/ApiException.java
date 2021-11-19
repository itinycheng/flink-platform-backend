package com.flink.platform.web.config.annotation;

import com.flink.platform.common.enums.ResponseStatus;

import static com.flink.platform.common.enums.ResponseStatus.SERVICE_ERROR;

/** Api exception. */
public @interface ApiException {

    ResponseStatus value() default SERVICE_ERROR;
}
