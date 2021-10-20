package com.flink.platform.common.job;

import com.flink.platform.common.enums.FunctionType;
import lombok.Data;

/** function udf. */
@Data
public class Function {

    private String name;

    private FunctionType type;

    private String clazz;
}
