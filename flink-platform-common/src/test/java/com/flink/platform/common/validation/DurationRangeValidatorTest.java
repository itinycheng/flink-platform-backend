package com.flink.platform.common.validation;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationRangeValidatorTest {

    static class Fixture {

        @DurationRange(min = "1m", max = "24h")
        Duration bounded;

        @DurationRange(min = "1s")
        Duration minOnly;

        @DurationRange
        Duration unbounded;
    }

    private static DurationRangeValidator validatorFor(String field) {
        DurationRange annotation;
        try {
            annotation = Fixture.class.getDeclaredField(field).getAnnotation(DurationRange.class);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }

        DurationRangeValidator validator = new DurationRangeValidator();
        validator.initialize(annotation);
        return validator;
    }

    @Test
    void acceptsValuesWithinBoundsInclusive() {
        DurationRangeValidator validator = validatorFor("bounded");
        assertTrue(validator.isValid(Duration.ofMinutes(1), null));
        assertTrue(validator.isValid(Duration.ofHours(12), null));
        assertTrue(validator.isValid(Duration.ofHours(24), null));
    }

    @Test
    void rejectsValuesOutsideBounds() {
        DurationRangeValidator validator = validatorFor("bounded");
        assertFalse(validator.isValid(Duration.ofSeconds(59), null));
        assertFalse(validator.isValid(Duration.ofHours(24).plusMillis(1), null));
    }

    @Test
    void rejectsZeroAndNegativeAgainstPositiveMin() {
        DurationRangeValidator validator = validatorFor("minOnly");
        assertFalse(validator.isValid(Duration.ZERO, null));
        assertFalse(validator.isValid(Duration.ofSeconds(-1), null));
    }

    @Test
    void appliesNoUpperBoundWhenMaxIsOmitted() {
        DurationRangeValidator validator = validatorFor("minOnly");
        assertTrue(validator.isValid(Duration.ofDays(3650), null));
    }

    @Test
    void acceptsAnythingWhenBothBoundsAreOmitted() {
        DurationRangeValidator validator = validatorFor("unbounded");
        assertTrue(validator.isValid(Duration.ofSeconds(-1), null));
        assertTrue(validator.isValid(Duration.ofDays(3650), null));
    }

    @Test
    void treatsNullAsValidSoNotNullStaysResponsible() {
        assertTrue(validatorFor("minOnly").isValid(null, null));
    }
}
