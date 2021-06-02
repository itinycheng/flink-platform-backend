package com.itiger.persona.flink.udf.common;

import com.itiger.persona.common.enums.DataType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * sql column description
 *
 * @author tiny.wang
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SqlColumn {

    int priority() default 0;

    String name() default "";

    DataType type() default DataType.STRING;

}
