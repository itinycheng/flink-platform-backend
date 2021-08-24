package com.flink.platform.common.job;

import com.flink.platform.common.enums.SqlType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * sql command
 *
 * @author tiny.wang
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Sql {

    private SqlType type;

    private String[] operands;

}
