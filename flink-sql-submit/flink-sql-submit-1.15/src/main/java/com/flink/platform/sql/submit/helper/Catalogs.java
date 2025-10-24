package com.flink.platform.sql.submit.helper;

import com.flink.platform.common.exception.FlinkJobGenException;
import com.flink.platform.common.job.Catalog;
import com.flink.platform.sql.submit.common.FlinkEnvironment;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/** register catalogs to table environment. */
public class Catalogs {

    public static void registerCatalogsToTableEnv(FlinkEnvironment env, List<Catalog> catalogs) {
        catalogs.forEach(catalog -> {
            if (StringUtils.isBlank(catalog.getCreateSql())) {
                throw new FlinkJobGenException(String.format("Create sql is empty, catalog: %s", catalog));
            }
            env.executeSql(catalog.getCreateSql());
        });
    }
}
