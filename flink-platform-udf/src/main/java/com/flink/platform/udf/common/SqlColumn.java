package com.flink.platform.udf.common;

import com.flink.platform.common.enums.DataType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Sql column description. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SqlColumn {

    int priority() default 0;

    String name() default "";

    DataType type() default DataType.STRING;
}
