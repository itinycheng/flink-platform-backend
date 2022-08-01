package com.flink.platform.core.helper;

import org.apache.flink.connector.clickhouse.catalog.ClickHouseCatalog;
import org.apache.flink.connector.clickhouse.config.ClickHouseConfig;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.catalog.GenericInMemoryCatalog;
import org.apache.flink.table.catalog.hive.HiveCatalog;

import com.flink.platform.common.exception.FlinkJobGenException;
import com.flink.platform.common.job.Catalog;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/** register catalogs to table environment. */
public class Catalogs {

    public static void registerCatalogsToTableEnv(TableEnvironment tEnv, List<Catalog> catalogs) {
        catalogs.forEach(
                catalog -> {
                    if (StringUtils.isNotBlank(catalog.getCreateSql())) {
                        tEnv.executeSql(catalog.getCreateSql());
                    } else {
                        addCatalog(tEnv, catalog);
                    }
                });
    }

    /** Better use sql instead. */
    @Deprecated
    private static void addCatalog(TableEnvironment tEnv, Catalog catalog) {
        switch (catalog.getType()) {
            case HIVE:
                HiveCatalog hiveCatalog =
                        new HiveCatalog(
                                catalog.getName(),
                                catalog.getDefaultDatabase(),
                                catalog.getConfigPath());
                tEnv.registerCatalog(catalog.getName(), hiveCatalog);
                break;
            case CLICKHOUSE:
                catalog.getConfigs()
                        .put(ClickHouseConfig.DATABASE_NAME, catalog.getDefaultDatabase());
                ClickHouseCatalog clickHouseCatalog =
                        new ClickHouseCatalog(catalog.getName(), catalog.getConfigs());
                tEnv.registerCatalog(catalog.getName(), clickHouseCatalog);
                break;
            case MEMORY:
                GenericInMemoryCatalog memoryCatalog =
                        new GenericInMemoryCatalog(catalog.getName(), catalog.getDefaultDatabase());
                tEnv.registerCatalog(catalog.getName(), memoryCatalog);
                break;
            default:
                throw new FlinkJobGenException(
                        String.format(
                                "Calling %s catalog from API isn't supported yet, please use create statement instead.",
                                catalog.getType().name()));
        }
    }
}
