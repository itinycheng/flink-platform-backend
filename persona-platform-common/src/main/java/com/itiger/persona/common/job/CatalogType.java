package com.itiger.persona.common.job;

import lombok.Getter;

/**
 * @author tiny.wang
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