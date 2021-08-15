package com.itiger.persona.common.entity.job;

import com.itiger.persona.common.enums.CatalogType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * @author tiny.wang
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Catalog {

    private String name;

    private CatalogType type;

    private String defaultDatabase;

    private String configPath;

    private Map<String, String> configs;
}
