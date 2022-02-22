package com.flink.platform.core.helper;

import org.apache.flink.connector.clickhouse.catalog.ClickHouseCatalog;
import org.apache.flink.connector.clickhouse.config.ClickHouseConfig;
import org.apache.flink.connector.jdbc.catalog.JdbcCatalog;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.catalog.GenericInMemoryCatalog;
import org.apache.flink.table.catalog.hive.HiveCatalog;

import com.flink.platform.common.exception.FlinkJobGenException;
import com.flink.platform.common.job.Catalog;
import com.flink.platform.common.util.Preconditions;
import io.tidb.bigdata.flink.tidb.TiDBCatalog;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.constants.JobConstant.JDBC_PASSWORD;
import static com.flink.platform.common.constants.JobConstant.JDBC_URL;
import static com.flink.platform.common.constants.JobConstant.JDBC_USERNAME;
import static com.flink.platform.common.constants.JobConstant.TIDB_DATABASE_URL;
import static com.flink.platform.common.constants.JobConstant.TIDB_PASSWORD;
import static com.flink.platform.common.constants.JobConstant.TIDB_USERNAME;

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
            case JDBC:
                checkJdbcConfigs(catalog);
                Map<String, String> configs = catalog.getConfigs();
                String url = configs.get(JDBC_URL);
                String username = configs.get(JDBC_USERNAME);
                String password = configs.get(JDBC_PASSWORD);
                JdbcCatalog jdbcCatalog =
                        new JdbcCatalog(
                                catalog.getName(),
                                catalog.getDefaultDatabase(),
                                username,
                                password,
                                url);
                tEnv.registerCatalog(catalog.getName(), jdbcCatalog);
                break;
            case TIDB:
                checkTidbConfigs(catalog);
                TiDBCatalog tidbcatalog =
                        new TiDBCatalog(
                                catalog.getName(),
                                catalog.getDefaultDatabase(),
                                catalog.getConfigs());
                tidbcatalog.open();
                tEnv.registerCatalog(catalog.getName(), tidbcatalog);
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
            case ICEBERG:
            default:
                throw new FlinkJobGenException("Unsupported catalog type, catalog: " + catalog);
        }
    }

    public static Map<String, String> getSubConf(Map<String, String> configs, String prefix) {
        final Map<String, String> props = new HashMap<>();
        configs.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .forEach(
                        key -> {
                            final String value = configs.get(key);
                            final String subKey = key.substring((prefix).length());
                            props.put(subKey, value);
                        });
        return props;
    }

    private static void checkJdbcConfigs(Catalog catalog) {
        Map<String, String> configs = catalog.getConfigs();
        Preconditions.checkThrow(
                configs.get(JDBC_URL) == null,
                () ->
                        new FlinkJobGenException(
                                String.format("jdbc url is null, catalog: %s", catalog)));
        Preconditions.checkThrow(
                configs.get(JDBC_USERNAME) == null,
                () ->
                        new FlinkJobGenException(
                                String.format("jdbc username is null, catalog: %s", catalog)));
        Preconditions.checkThrow(
                configs.get(JDBC_PASSWORD) == null,
                () ->
                        new FlinkJobGenException(
                                String.format("jdbc password is null, catalog: %s", catalog)));
    }

    private static void checkTidbConfigs(Catalog catalog) {
        Map<String, String> configs = catalog.getConfigs();
        Preconditions.checkThrow(
                configs.get(TIDB_DATABASE_URL) == null,
                () ->
                        new FlinkJobGenException(
                                String.format("jdbc url is null, catalog: %s", catalog)));
        Preconditions.checkThrow(
                configs.get(TIDB_USERNAME) == null,
                () ->
                        new FlinkJobGenException(
                                String.format("jdbc username is null, catalog: %s", catalog)));
        Preconditions.checkThrow(
                configs.get(TIDB_PASSWORD) == null,
                () ->
                        new FlinkJobGenException(
                                String.format("jdbc password is null, catalog: %s", catalog)));
    }
}
