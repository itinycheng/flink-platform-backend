package com.flink.platform.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bounds a {@link java.time.Duration} property, the {@code @Min}/{@code @Max} equivalent for time spans.
 *
 * <p>Bounds are written the same way the property itself is written in yaml (e.g. {@code "30s"}, {@code "24h"}) and are
 * parsed by {@link com.flink.platform.common.util.DurationUtil}. An empty bound means unbounded on that side.
 *
 * <p>Per Jakarta Bean Validation convention a {@code null} value is considered valid; pair with {@code @NotNull} when
 * the property is required.
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DurationRangeValidator.class)
public @interface DurationRange {

    String min() default "";

    String max() default "";

    String message() default "must be a duration between '{min}' and '{max}'";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
