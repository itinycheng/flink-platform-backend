package com.flink.platform.common.job;

import com.flink.platform.common.enums.ExecutionMode;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** sql context. */
@Data
public class SqlContext {

    private String id;

    private List<Sql> sqls;

    private ExecutionMode execMode;

    private List<String> extJars;

    /** flink-conf.yaml configs. */
    private Map<String, String> configs;

    private List<Catalog> catalogs;

    /** currently not in use. */
    private List<Function> functions;
}
