package com.flink.platform.udf;

import com.flink.platform.common.constants.Constant;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Unit test for simple App. */
class UdfTest {
    @Test
    void test0() {
        System.out.println(Arrays.toString("a|b|c".split(Constant.OR)));
        System.out.println(Arrays.toString("a&b&c".split(Constant.AND)));
    }

    @Test
    void test1() {
        List<Integer> integers = Arrays.asList(1, 2, 3);
        integers.contains("a");
        Class<?> aClass = integers.get(0).getClass();
        System.out.println(aClass);
    }

    @Test
    void test2() {
        Pattern p = Pattern.compile("(^\\d{4}-\\d{1,2}-\\d{1,2})");

        String date = "1990-03-21";

        Matcher m = p.matcher(date);
        if (m.find()) {
            date = m.group(1);
            String replace = date.replace("-", "");
            System.out.println(replace);
        }
    }
}
