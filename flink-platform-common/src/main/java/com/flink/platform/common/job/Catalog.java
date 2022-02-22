package com.flink.platform.common.job;

import com.flink.platform.common.enums.CatalogType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** flink catalog. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Catalog {

    private String name;

    private CatalogType type;

    @Deprecated private String defaultDatabase;

    @Deprecated private String configPath;

    @Deprecated private Map<String, String> configs;

    private String createSql;
}
