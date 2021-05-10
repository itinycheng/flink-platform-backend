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
public class SqlSelect {

    private List<String> selectList;

    private String from;

    private Condition where;

}
