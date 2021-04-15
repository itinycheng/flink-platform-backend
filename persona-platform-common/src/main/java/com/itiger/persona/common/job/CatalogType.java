package com.itiger.persona.common.job;

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