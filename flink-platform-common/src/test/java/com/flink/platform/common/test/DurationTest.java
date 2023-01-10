/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flink.platform.common.test;

import com.flink.platform.common.util.DurationUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** Tests for Duration, copy from flink. */
public class DurationTest {

    @Test
    public void testParseNanos() {
        assertEquals(424562, DurationUtil.parse("424562ns").getNano());
        assertEquals(424562, DurationUtil.parse("424562nano").getNano());
        assertEquals(424562, DurationUtil.parse("424562nanos").getNano());
        assertEquals(424562, DurationUtil.parse("424562nanosecond").getNano());
        assertEquals(424562, DurationUtil.parse("424562nanoseconds").getNano());
        assertEquals(424562, DurationUtil.parse("424562 ns").getNano());
    }

    @Test
    public void testParseMicros() {
        assertEquals(565731 * 1000L, DurationUtil.parse("565731µs").getNano());
        assertEquals(565731 * 1000L, DurationUtil.parse("565731micro").getNano());
        assertEquals(565731 * 1000L, DurationUtil.parse("565731micros").getNano());
        assertEquals(565731 * 1000L, DurationUtil.parse("565731microsecond").getNano());
        assertEquals(565731 * 1000L, DurationUtil.parse("565731microseconds").getNano());
        assertEquals(565731 * 1000L, DurationUtil.parse("565731 µs").getNano());
    }

    @Test
    public void testParseMillis() {
        assertEquals(1234, DurationUtil.parse("1234").toMillis());
        assertEquals(1234, DurationUtil.parse("1234ms").toMillis());
        assertEquals(1234, DurationUtil.parse("1234milli").toMillis());
        assertEquals(1234, DurationUtil.parse("1234millis").toMillis());
        assertEquals(1234, DurationUtil.parse("1234millisecond").toMillis());
        assertEquals(1234, DurationUtil.parse("1234milliseconds").toMillis());
        assertEquals(1234, DurationUtil.parse("1234 ms").toMillis());
    }

    @Test
    public void testParseSeconds() {
        assertEquals(667766, DurationUtil.parse("667766s").getSeconds());
        assertEquals(667766, DurationUtil.parse("667766sec").getSeconds());
        assertEquals(667766, DurationUtil.parse("667766secs").getSeconds());
        assertEquals(667766, DurationUtil.parse("667766second").getSeconds());
        assertEquals(667766, DurationUtil.parse("667766seconds").getSeconds());
        assertEquals(667766, DurationUtil.parse("667766 s").getSeconds());
    }

    @Test
    public void testParseMinutes() {
        assertEquals(7657623, DurationUtil.parse("7657623m").toMinutes());
        assertEquals(7657623, DurationUtil.parse("7657623min").toMinutes());
        assertEquals(7657623, DurationUtil.parse("7657623minute").toMinutes());
        assertEquals(7657623, DurationUtil.parse("7657623minutes").toMinutes());
        assertEquals(7657623, DurationUtil.parse("7657623 min").toMinutes());
    }

    @Test
    public void testParseHours() {
        assertEquals(987654, DurationUtil.parse("987654h").toHours());
        assertEquals(987654, DurationUtil.parse("987654hour").toHours());
        assertEquals(987654, DurationUtil.parse("987654hours").toHours());
        assertEquals(987654, DurationUtil.parse("987654 h").toHours());
    }

    @Test
    public void testParseDays() {
        assertEquals(987654, DurationUtil.parse("987654d").toDays());
        assertEquals(987654, DurationUtil.parse("987654day").toDays());
        assertEquals(987654, DurationUtil.parse("987654days").toDays());
        assertEquals(987654, DurationUtil.parse("987654 d").toDays());
    }

    @Test
    public void testParseUpperCase() {
        assertEquals(1L, DurationUtil.parse("1 NS").toNanos());
        assertEquals(1000L, DurationUtil.parse("1 MICRO").toNanos());
        assertEquals(1L, DurationUtil.parse("1 MS").toMillis());
        assertEquals(1L, DurationUtil.parse("1 S").getSeconds());
        assertEquals(1L, DurationUtil.parse("1 MIN").toMinutes());
        assertEquals(1L, DurationUtil.parse("1 H").toHours());
        assertEquals(1L, DurationUtil.parse("1 D").toDays());
    }

    @Test
    public void testParseTrim() {
        assertEquals(155L, DurationUtil.parse("      155      ").toMillis());
        assertEquals(155L, DurationUtil.parse("      155      ms   ").toMillis());
    }

    @Test
    public void testParseInvalid() {
        // empty
        try {
            DurationUtil.parse("");
            fail("exception expected");
        } catch (IllegalArgumentException ignored) {
        }

        // blank
        try {
            DurationUtil.parse("     ");
            fail("exception expected");
        } catch (IllegalArgumentException ignored) {
        }

        // no number
        try {
            DurationUtil.parse("foobar or fubar or foo bazz");
            fail("exception expected");
        } catch (IllegalArgumentException ignored) {
        }

        // wrong unit
        try {
            DurationUtil.parse("16 gjah");
            fail("exception expected");
        } catch (IllegalArgumentException ignored) {
        }

        // multiple numbers
        try {
            DurationUtil.parse("16 16 17 18 ms");
            fail("exception expected");
        } catch (IllegalArgumentException ignored) {
        }

        // negative number
        try {
            DurationUtil.parse("-100 ms");
            fail("exception expected");
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseNumberOverflow() {
        DurationUtil.parse("100000000000000000000000000000000 ms");
    }
}
