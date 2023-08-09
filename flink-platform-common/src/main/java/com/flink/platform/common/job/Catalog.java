package com.flink.platform.common.job;

import com.flink.platform.common.enums.CatalogType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** flink catalog. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Catalog {

    private String name;

    private CatalogType type;

    private String createSql;
}
