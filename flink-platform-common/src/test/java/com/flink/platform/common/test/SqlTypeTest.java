package com.flink.platform.common.test;

import com.flink.platform.common.enums.SqlType;
import org.junit.Test;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

public class SqlTypeTest {
    @Test
    public void test1() {
        System.out.println(SqlType.parse("create     "));
        System.out.println(SqlType.parse("create     view"));
        System.out.println(SqlType.parse("create \n view"));
        System.out.println(SqlType.parse("create \r\n view"));
        System.out.println(SqlType.parse("create TEMPORARY view"));
        System.out.println("~end1~");
    }

    @Test
    public void test2() {
        System.out.println("create \n\n\n d".trim());
        System.out.println("\n\n\ncreate \n\n\n d".trim());
        System.out.println("~end2~");
    }

    @Test
    public void test3() {
        System.out.println(SqlType.parse("create  function     "));
        System.out.println(SqlType.parse("create   TEMPORARY   function "));
        System.out.println(SqlType.parse("create \n   TEMPORARY  \n  SYSTEM  \n  function   "));
        System.out.println(SqlType.parse("create \n   TEMPORARY error \n  SYSTEM  \n  function   "));
        System.out.println("~end3~");
    }

    @Test
    public void test4() {
        Stream.iterate(0, i -> i + 1).limit(3).forEach(System.out::println);
    }

    @Test
    public void test5() {
        System.out.println(SqlType.parse("insert  into  table where a = 1   "));
        System.out.println(SqlType.parse("insert  \n  overwrite "));
        System.out.println(SqlType.parse("insert   "));
        System.out.println("~end5~");
    }

    @Test
    public void test6() {
        System.out.println(SqlType.parse("set  \n a =  b   "));
        System.out.println("~end6~");
    }

    @Test
    public void test7() {
        Map<Integer, String> map = new TreeMap<>();
        map.put(1, "1");
        map.put(0, "0");
        map.put(2, "2");
        map.forEach((k, v) -> System.out.println(k + ", " + v));
    }
}
