package com.itiger.persona.common.job;

import lombok.Data;

import java.util.Map;

/**
 * @author tiny.wang
 */
@Data
public class Catalog {

    private String name;

    private CatalogType type;

    private String defaultDatabase;

    private String configPath;

    private Map<String, String> configs;
}
