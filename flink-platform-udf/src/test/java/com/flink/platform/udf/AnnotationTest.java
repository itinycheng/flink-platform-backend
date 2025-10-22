package com.flink.platform.udf;

import com.flink.platform.udf.business.PositionParserFunction;
import org.junit.jupiter.api.Test;

/** Annotation test. */
class AnnotationTest {

    @Test
    void test() {
        PositionParserFunction func = new PositionParserFunction();
        System.out.println(func);
    }
}
