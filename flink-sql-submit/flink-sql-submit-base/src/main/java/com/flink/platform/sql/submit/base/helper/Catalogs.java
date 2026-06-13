package com.flink.platform.sql.submit.base.helper;

import com.flink.platform.common.exception.FlinkJobGenException;
import com.flink.platform.common.job.Catalog;
import com.flink.platform.sql.submit.base.common.FlinkEnvAdapter;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/** Register catalogs to a Flink table environment via {@link FlinkEnvAdapter}. */
public class Catalogs {

    public static void registerCatalogsToTableEnv(FlinkEnvAdapter env, List<Catalog> catalogs) {
        catalogs.forEach(catalog -> {
            if (StringUtils.isBlank(catalog.getCreateSql())) {
                throw new FlinkJobGenException(String.format("Create sql is empty, catalog: %s", catalog));
            }
            env.executeAndPrint(catalog.getCreateSql());
        });
    }
}
