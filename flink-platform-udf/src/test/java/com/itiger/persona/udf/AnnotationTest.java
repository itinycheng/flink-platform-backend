package com.itiger.persona.udf;

import com.itiger.persona.flink.udf.business.PositionParserFunction;
import org.junit.Test;

public class AnnotationTest {

    @Test
    public void test() {
        PositionParserFunction func = new PositionParserFunction();
        System.out.println(func);
    }
}
