package com.itiger.persona.common.entity.job;

import com.itiger.persona.common.enums.FunctionType;
import lombok.Data;

/**
 * function udf
 *
 * @author tiny.wang
 */
@Data
public class Function {

    private String name;

    private FunctionType type;

    private String clazz;

}
