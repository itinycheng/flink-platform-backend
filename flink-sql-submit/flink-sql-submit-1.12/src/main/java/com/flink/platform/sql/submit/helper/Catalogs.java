package com.flink.platform.sql.submit.helper;

import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.catalog.GenericInMemoryCatalog;

import com.flink.platform.common.enums.CatalogType;
import com.flink.platform.common.exception.FlinkJobGenException;
import com.flink.platform.common.job.Catalog;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/** register catalogs to table environment. */
public class Catalogs {

    public static void registerCatalogsToTableEnv(TableEnvironment tEnv, List<Catalog> catalogs) {
        catalogs.forEach(catalog -> {
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
        if (catalog.getType() == CatalogType.MEMORY) {
            GenericInMemoryCatalog memoryCatalog = new GenericInMemoryCatalog(catalog.getName(), "default");
            tEnv.registerCatalog(catalog.getName(), memoryCatalog);
        } else {
            throw new FlinkJobGenException(String.format(
                    "Calling %s catalog from API isn't supported yet, please use create statement instead.",
                    catalog.getType().name()));
        }
    }
}
