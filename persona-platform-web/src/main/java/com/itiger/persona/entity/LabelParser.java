package com.itiger.persona.entity;

import com.itiger.persona.flink.udf.common.SqlColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author tiny.wang
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LabelParser {

    private String functionName;

    private Class<?> functionClass;

    private Class<?> dataClass;

    private List<SqlColumn> dataColumns;

}
