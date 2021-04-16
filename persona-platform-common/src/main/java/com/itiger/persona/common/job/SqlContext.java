package com.itiger.persona.common.job;

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

    private List<SqlCommand> sqls;

    private ExecutionMode execMode;

    /**
     * currently not in use
     */
    private List<String> extJars;

    private Map<String, String> configs;

    private List<Catalog> catalogs;

    private List<Function> functions;
}
