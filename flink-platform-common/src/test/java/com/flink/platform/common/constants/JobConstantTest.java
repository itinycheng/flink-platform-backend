package com.flink.platform.common.constants;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobConstantTest {

    @Test
    void timePatternMatchesCurDay() {
        Matcher m = JobConstant.TIME_PATTERN.matcher("${time:yyyyMMdd[curDay]}");
        assertTrue(m.find());
        assertEquals("curDay", m.group("baseTime"));
    }

    @Test
    void timePatternMatchesBizDay() {
        Matcher m = JobConstant.TIME_PATTERN.matcher("${time:yyyyMMdd[bizDay]}");
        assertTrue(m.find());
        assertEquals("bizDay", m.group("baseTime"));
    }

    @Test
    void timePatternMatchesBizDayWithOffset() {
        Matcher m = JobConstant.TIME_PATTERN.matcher("${time:yyyyMMdd[bizDay-1d]}");
        assertTrue(m.find());
        assertEquals("bizDay", m.group("baseTime"));
        assertEquals("-", m.group("operator"));
        assertEquals("1d", m.group("duration"));
    }

    @Test
    void timePatternMatchesAllBizUnits() {
        for (String unit :
                new String[] {"bizYear", "bizMonth", "bizDay", "bizHour", "bizMinute", "bizSecond", "bizMillisecond"}) {
            Matcher m = JobConstant.TIME_PATTERN.matcher("${time:yyyy-MM-dd HH:mm:ss.SSS[" + unit + "]}");
            assertTrue(m.find(), "should match " + unit);
            assertEquals(unit, m.group("baseTime"));
        }
    }
}
