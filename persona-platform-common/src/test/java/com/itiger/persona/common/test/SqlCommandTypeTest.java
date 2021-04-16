package com.itiger.persona.common.test;

import com.itiger.persona.common.job.SqlCommandType;
import org.junit.Test;

import java.util.stream.Stream;

public class SqlCommandTypeTest {
    @Test
    public void test1(){
        System.out.println(SqlCommandType.parse("create     "));
        System.out.println(SqlCommandType.parse("create     view"));
        System.out.println(SqlCommandType.parse("create \n view"));
        System.out.println(SqlCommandType.parse("create \r\n view"));
        System.out.println(SqlCommandType.parse("create TEMPORARY view"));
        System.out.println("~end1~");
    }

    @Test
    public void test2(){
        System.out.println("create \n\n\n d".trim());
        System.out.println("\n\n\ncreate \n\n\n d".trim());
        System.out.println("~end2~");
    }

    @Test
    public void test3(){
        System.out.println(SqlCommandType.parse("create  function     "));
        System.out.println(SqlCommandType.parse("create   TEMPORARY   function "));
        System.out.println(SqlCommandType.parse("create \n   TEMPORARY  \n  SYSTEM  \n  function   "));
        System.out.println(SqlCommandType.parse("create \n   TEMPORARY error \n  SYSTEM  \n  function   "));
        System.out.println("~end3~");
    }

    @Test
    public void test4(){
        Stream.iterate(0, i -> i + 1).limit(3).forEach(System.out::println);
    }

    @Test
    public void test5(){
        System.out.println(SqlCommandType.parse("insert  into  table where a = 1   "));
        System.out.println(SqlCommandType.parse("insert  \n  overwrite "));
        System.out.println(SqlCommandType.parse("insert   "));
        System.out.println("~end5~");
    }

    @Test
    public void test6(){
        System.out.println(SqlCommandType.parse("set  \n a =  b   "));
        System.out.println("~end5~");
    }

}
