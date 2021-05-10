package com.itiger.persona.parser;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author tiny.wang
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimpleCondition extends Condition {

    private String operator;

    private String[] operands;

}
