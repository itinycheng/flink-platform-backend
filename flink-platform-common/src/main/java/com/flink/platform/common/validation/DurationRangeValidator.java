package com.flink.platform.common.validation;

import com.flink.platform.common.util.DurationUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * Validator for {@link DurationRange}.
 */
public class DurationRangeValidator implements ConstraintValidator<DurationRange, Duration> {

    @Nullable
    private Duration min;

    @Nullable
    private Duration max;

    @Override
    public void initialize(DurationRange annotation) {
        min = parseBound(annotation.min());
        max = parseBound(annotation.max());
    }

    @Override
    public boolean isValid(@Nullable Duration value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        return (min == null || value.compareTo(min) >= 0) && (max == null || value.compareTo(max) <= 0);
    }

    @Nullable
    private static Duration parseBound(String bound) {
        return bound.isEmpty() ? null : DurationUtil.parse(bound);
    }
}
