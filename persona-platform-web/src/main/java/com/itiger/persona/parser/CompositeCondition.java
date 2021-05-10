package com.itiger.persona.parser;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author tiny.wang
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompositeCondition extends Condition {

    private String relation;

    private List<Condition> conditions;

}
