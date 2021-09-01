package com.flink.platform.web.parser;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author tiny.wang
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SqlLimit {

    private int offset;

    private int rowCount;

}