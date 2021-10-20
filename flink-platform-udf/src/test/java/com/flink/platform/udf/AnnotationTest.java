package com.flink.platform.udf;

import com.flink.platform.udf.business.PositionParserFunction;
import org.junit.Test;

/** Annotation test. */
public class AnnotationTest {

    @Test
    public void test() {
        PositionParserFunction func = new PositionParserFunction();
        System.out.println(func);
    }
}
