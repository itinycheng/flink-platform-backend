package com.flink.platform.common.constants;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void timePatternMatchesWhitespaceAroundBaseTimeAndOffset() {
        String[][] cases = {
            {"${time:yyyyMMdd[bizDay - 1d]}", "bizDay", "-", "1d"},
            {"${time:yyyyMMdd[ bizDay + 2d ]}", "bizDay", "+", "2d"},
            {"${time:yyyyMMdd[ bizDay ]}", "bizDay", null, null},
            {"${time:yyyyMMdd[curHour- 30m]}", "curHour", "-", "30m"},
            {"${time:yyyyMMdd[BIZDAY - 1d]}", "BIZDAY", "-", "1d"}
        };
        for (String[] testCase : cases) {
            Matcher m = JobConstant.TIME_PATTERN.matcher(testCase[0]);
            assertTrue(m.matches(), testCase[0]);
            assertEquals("yyyyMMdd", m.group("format"), testCase[0]);
            assertEquals(testCase[1], m.group("baseTime"), testCase[0]);
            assertEquals(testCase[2], m.group("operator"), testCase[0]);
            assertEquals(testCase[3], m.group("duration"), testCase[0]);
        }
    }

    @Test
    void timePatternMatchesWithoutBracket() {
        Matcher m = JobConstant.TIME_PATTERN.matcher("${time:yyyyMMdd}");
        assertTrue(m.find());
        assertEquals("yyyyMMdd", m.group("format"));
        assertNull(m.group("baseTime"));
        assertNull(m.group("operator"));
        assertNull(m.group("duration"));
    }

    @Test
    void timePatternMatchesWithoutBracketWithSeparators() {
        Matcher m = JobConstant.TIME_PATTERN.matcher("${time:yyyy-MM-dd HH:mm:ss}");
        assertTrue(m.find());
        assertEquals("yyyy-MM-dd HH:mm:ss", m.group("format"));
        assertNull(m.group("baseTime"));
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
