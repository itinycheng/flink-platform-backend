package com.itiger.persona.common.job;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @author tiger
 */
@Data
public class SqlContext {

    private String id;

    private String sql;

    private ExecutionMode execMode;

    private String extJar;

    private Map<String, String> config;

    private List<Catalog> catalogs;
}
