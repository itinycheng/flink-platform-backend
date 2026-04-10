package com.flink.platform.common.annotation;

import com.flink.platform.common.enums.EntityType;
import com.flink.platform.common.enums.OperationType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a service method for automatic audit logging. */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** Entity type being audited. */
    EntityType type();

    /** The operation being performed. */
    OperationType operation();
}
