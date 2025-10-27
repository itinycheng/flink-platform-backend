package com.flink.platform.common.test;

import org.junit.jupiter.api.Test;

import static com.flink.platform.common.util.StringUtil.byteLength;
import static com.flink.platform.common.util.StringUtil.truncateByBytes;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * string util test.
 */
class StringUtilTest {

    @Test
    void testTruncateByBytes() {
        String testStr = "你好，Hello, こんにちは";
        System.out.println(truncateByBytes(testStr, 2, true));
        System.out.println(truncateByBytes(testStr, 5, true));
        System.out.println(truncateByBytes(testStr, 10, true));
        System.out.println(truncateByBytes(testStr, 10, false));
        System.out.println(truncateByBytes(testStr, 15, true));
        System.out.println(truncateByBytes(testStr, 15, false));
        System.out.println(truncateByBytes(testStr, 20, true));
        System.out.println(truncateByBytes(testStr, 20, false));
        assertTrue(byteLength(truncateByBytes(testStr, 15, false)) <= 15);
        assertTrue(byteLength(truncateByBytes(testStr, 15, true)) <= 15);
        assertTrue(byteLength(truncateByBytes(testStr, 20, false)) <= 20);
        assertTrue(byteLength(truncateByBytes(testStr, 20, true)) <= 20);
    }
}
