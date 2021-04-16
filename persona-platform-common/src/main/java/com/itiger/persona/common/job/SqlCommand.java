package com.itiger.persona.common.job;

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
public class SqlCommand {

    private SqlCommandType type;

    private String[] operands;

}
