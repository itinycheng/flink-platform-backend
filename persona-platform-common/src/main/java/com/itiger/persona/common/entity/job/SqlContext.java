package com.itiger.persona.common.entity.job;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * sql context
 *
 * @author tiny.wang
 */
@Data
public class SqlContext {

    private String id;

    private List<Sql> sqls;

    private ExecutionMode execMode;

    /**
     * currently not in use
     */
    private List<String> extJars;

    /**
     * flink-conf.yaml configs
     */
    private Map<String, String> configs;

    private List<Catalog> catalogs;

    /**
     * currently not in use
     */
    private List<Function> functions;
}
