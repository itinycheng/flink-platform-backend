package com.flink.platform.common.test;

import org.junit.Assert;
import org.junit.Test;

import static com.flink.platform.common.util.StringUtil.byteLength;
import static com.flink.platform.common.util.StringUtil.truncateByBytes;

/**
 * string util test.
 */
public class StringUtilTest {

    @Test
    public void testTruncateByBytes() {
        String testStr = "你好，Hello, こんにちは";
        System.out.println(truncateByBytes(testStr, 2, true));
        System.out.println(truncateByBytes(testStr, 5, true));
        System.out.println(truncateByBytes(testStr, 10, true));
        System.out.println(truncateByBytes(testStr, 10, false));
        System.out.println(truncateByBytes(testStr, 15, true));
        System.out.println(truncateByBytes(testStr, 15, false));
        System.out.println(truncateByBytes(testStr, 20, true));
        System.out.println(truncateByBytes(testStr, 20, false));
        Assert.assertTrue(byteLength(truncateByBytes(testStr, 15, false)) <= 15);
        Assert.assertTrue(byteLength(truncateByBytes(testStr, 15, true)) <= 15);
        Assert.assertTrue(byteLength(truncateByBytes(testStr, 20, false)) <= 20);
        Assert.assertTrue(byteLength(truncateByBytes(testStr, 20, true)) <= 20);
    }
}
