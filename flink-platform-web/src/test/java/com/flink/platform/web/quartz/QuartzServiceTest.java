package com.flink.platform.web.quartz;

import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuartzServiceTest {

    private final QuartzService quartzService = new QuartzService();

    @Test
    void validatesCronExpressionAndMinimumInterval() throws Exception {
        assertTrue(quartzService.isValidCronExpression("0 0/10 * * * ?"));
        assertFalse(quartzService.isValidCronExpression("invalid"));
        assertTrue(quartzService.hasValidScheduleInterval("0 0/10 * * * ?"));
        assertFalse(quartzService.hasValidScheduleInterval("0/30 * * * * ?"));
        assertThrows(ParseException.class, () -> quartzService.hasValidScheduleInterval("invalid"));
    }

    @Test
    void calculatesNextTriggerTimeAfter() {
        var nextTriggerTime = quartzService.nextTriggerTimeAfter("0 0 * * * ?", LocalDateTime.of(2026, 9, 26, 16, 30));

        assertEquals(Optional.of(LocalDateTime.of(2026, 9, 26, 17, 0)), nextTriggerTime);
    }

    @Test
    void returnsEmptyWhenCronHasNoNextTriggerTime() {
        var nextTriggerTime =
                quartzService.nextTriggerTimeAfter("0 0 0 1 1 ? 2000", LocalDateTime.of(2026, 9, 26, 16, 30));

        assertTrue(nextTriggerTime.isEmpty());
    }
}
