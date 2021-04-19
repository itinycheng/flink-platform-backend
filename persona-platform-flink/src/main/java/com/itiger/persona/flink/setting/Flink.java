package com.itiger.persona.flink.setting;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * setting config
 * flink (root) node
 *
 * @author tiny.wang
 */
@Data
@NoArgsConstructor
public class Flink {
    private StreamSql streamSql;
}
