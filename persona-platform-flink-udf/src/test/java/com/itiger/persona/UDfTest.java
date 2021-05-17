package com.itiger.persona;

import com.itiger.persona.common.constants.Constant;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Unit test for simple App.
 */
public class UDfTest {
    @Test
    public void test0() {
        System.out.println(Arrays.toString("a|b|c".split(Constant.OR)));
        System.out.println(Arrays.toString("a&b&c".split(Constant.AND)));
    }

    @Test
    public void test1() {
        List<Integer> integers = Arrays.asList(1, 2, 3);
        integers.contains("a");
        Class<?> aClass = integers.get(0).getClass();
        System.out.println(aClass);
    }
}
