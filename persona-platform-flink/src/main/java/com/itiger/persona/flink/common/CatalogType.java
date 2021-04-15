package com.itiger.persona.flink.common;

import lombok.Getter;

/**
 * @author tiger
 */
@Getter
public enum CatalogType {

    /**
     * catalog type
     */
    MEMORY,

    HIVE,

    TIDB,

    JDBC,

    POSTGRES

}