package com.itiger.persona.common.job;

import lombok.Data;

import java.util.Map;

/**
 * @author tiger
 */
@Data
public class Catalog {

    private String name;

    private CatalogType type;

    private String defaultDatabase;

    private Map<String, String> config;
}
