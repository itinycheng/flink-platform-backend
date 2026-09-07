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

    /**
     * Whether to still write an audit row when the method returns a business failure.
     *
     * <p>Default {@code false}: a failed call is a pure rejection that changed nothing, so auditing
     * it would only add noise. Set to {@code true} for methods that can return a failure <b>after</b>
     * already having caused a side effect — otherwise the state change goes unrecorded.
     *
     * <p>Trade-off: the aspect cannot tell a rejection from a partial failure, so enabling this also
     * audits calls that were rejected outright.
     */
    boolean auditOnFailure() default false;
}
