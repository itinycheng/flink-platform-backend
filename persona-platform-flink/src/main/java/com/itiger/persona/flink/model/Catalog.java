package com.itiger.persona.flink.model;

import com.itiger.persona.flink.common.CatalogType;
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
