package com.flink.platform.sql.submit.helper;

import org.apache.flink.table.api.TableEnvironment;

import com.flink.platform.common.exception.FlinkJobGenException;
import com.flink.platform.common.job.Catalog;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/** register catalogs to table environment. */
public class Catalogs {

    public static void registerCatalogsToTableEnv(TableEnvironment tEnv, List<Catalog> catalogs) {
        catalogs.forEach(catalog -> {
            if (StringUtils.isBlank(catalog.getCreateSql())) {
                throw new FlinkJobGenException(String.format("Create sql is empty, catalog: %s", catalog));
            }
            tEnv.executeSql(catalog.getCreateSql());
        });
    }
}
