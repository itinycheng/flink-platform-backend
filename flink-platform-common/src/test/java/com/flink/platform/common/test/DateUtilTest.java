package com.flink.platform.common.test;

import com.flink.platform.common.util.DateUtil;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DateUtilTest {

    @Test
    void formatAndParseDateTime() {
        LocalDateTime dateTime = LocalDateTime.of(2026, 10, 10, 8, 30);

        assertEquals("2026-10-10 08:30:00", DateUtil.format(dateTime));
        assertEquals(dateTime, DateUtil.parse(DateUtil.format(dateTime)));
    }

    @Test
    void formatAndParseNull() {
        assertNull(DateUtil.formatNullable(null));
        assertNull(DateUtil.parseNullable(null));
    }
}
