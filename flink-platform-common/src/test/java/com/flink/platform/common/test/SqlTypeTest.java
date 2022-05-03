package com.flink.platform.common.test;

import com.flink.platform.common.enums.SqlType;
import org.junit.Test;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/** sql type test. */
public class SqlTypeTest {

    @Test
    public void test0() {
        System.out.println(SqlType.parse("select  \n * from table "));
        System.out.println(SqlType.parse("insert into \n table select "));
        System.out.println(SqlType.parse("insert \n overwrite     "));
        System.out.println(SqlType.parse("use db     "));
        System.out.println(SqlType.parse("use \n catalog \n test     "));
    }

    @Test
    public void test1() {
        System.out.println(SqlType.parse("create  \n database \n test   "));
        System.out.println(SqlType.parse("create  \n table \n test   "));
        System.out.println(SqlType.parse("create table \n test   "));
        System.out.println(SqlType.parse("create TEMPORARY \n table test"));
        System.out.println(SqlType.parse("create \n view"));
        System.out.println(SqlType.parse("create     view"));
        System.out.println(SqlType.parse("create view"));
        System.out.println(SqlType.parse("create \r\n view"));
        System.out.println(SqlType.parse("create TEMPORARY \n view"));
        System.out.println(SqlType.parse("create \n TEMPORARY \n view"));
        System.out.println(SqlType.parse("create  function     "));
        System.out.println(SqlType.parse("create   temporary   function "));
        System.out.println(SqlType.parse("create \n   temporary  \n  system  \n  function   "));
        // System.out.println(SqlType.parse("create \n   temporary error \n  system  \n  function
        // "));
        System.out.println("~end1~");
    }

    @Test
    public void test2() {
        System.out.println(SqlType.parse("drop\n database \n db"));
        System.out.println(SqlType.parse("drop\n table \n tab"));
        System.out.println(SqlType.parse("drop\n temporary \n view v"));
        System.out.println(SqlType.parse("drop\n table \n tab"));
        System.out.println(SqlType.parse("drop function f "));
        System.out.println(SqlType.parse("drop\n temporary \n function f "));
        System.out.println(SqlType.parse("drop\n temporary  \n system \n function f "));
        System.out.println("~end2~");
    }

    @Test
    public void test3() {
        System.out.println(SqlType.parse("alter\n database \n db"));
        System.out.println(SqlType.parse("alter\n table \n tb"));
        System.out.println(SqlType.parse("alter\n temporary \n function f"));
        System.out.println(SqlType.parse("alter\n temporary  \n system \n function f"));
        System.out.println("~end3~");
    }

    @Test
    public void test4() {
        Stream.iterate(0, i -> i + 1).limit(3).forEach(System.out::println);
    }

    @Test
    public void test5() {
        System.out.println(SqlType.parse("show  catalogs "));
        System.out.println(SqlType.parse("show  \n  databases "));
        System.out.println(SqlType.parse("show tables   "));
        System.out.println(SqlType.parse("show functions   "));
        System.out.println(SqlType.parse("show modules   "));
        System.out.println(SqlType.parse("describe tab   "));
        System.out.println(SqlType.parse("explain plan for  \n select "));
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

    @Test
    public void test8() {
        System.out.println(SqlType.parse("select  \n * from table limit 10"));
        System.out.println(SqlType.parse("select  \n * from table limit \n   10000"));
        System.out.println(SqlType.parse("select  \n * from table limit 2,  10000"));
    }
}
