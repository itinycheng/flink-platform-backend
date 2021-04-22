package com.itiger.persona.common.entity.job;

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
