package com.flink.platform.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the supported version range: [{@code minVersion}, {@code maxVersion}).
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface VersionRange {

    /** Minimum supported version (inclusive). */
    String minVersion();

    /**
     * Maximum supported version (exclusive). <br> Empty means no upper bound.
     */
    String maxVersion() default "";
}
