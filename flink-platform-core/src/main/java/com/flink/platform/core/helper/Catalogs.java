package com.flink.platform.core.helper;

import org.apache.flink.connector.clickhouse.catalog.ClickHouseCatalog;
import org.apache.flink.connector.clickhouse.config.ClickHouseConfig;
import org.apache.flink.connector.jdbc.catalog.JdbcCatalog;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.catalog.GenericInMemoryCatalog;
import org.apache.flink.table.catalog.hive.HiveCatalog;

import com.flink.platform.common.constants.JobConstant;
import com.flink.platform.common.exception.FlinkJobGenException;
import com.flink.platform.common.job.Catalog;
import com.flink.platform.common.util.Preconditions;
import io.tidb.bigdata.flink.tidb.TiDBCatalog;

import java.util.List;
import java.util.Map;

/** register catalogs to table environment. */
public class Catalogs {

    public static void registerCatalogsToTableEnv(TableEnvironment tEnv, List<Catalog> catalogs) {
        catalogs.forEach(catalog -> addCatalog(tEnv, catalog));
        // TODO set current catalog
        catalogs.stream().findFirst().ifPresent(catalog -> tEnv.useCatalog(catalog.getName()));
    }

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
                String url = configs.get(JobConstant.JDBC_URL);
                String username = configs.get(JobConstant.JDBC_USERNAME);
                String password = configs.get(JobConstant.JDBC_PASSWORD);
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
            default:
                throw new FlinkJobGenException("Unsupported catalog type, catalog: " + catalog);
        }
    }

    private static void checkJdbcConfigs(Catalog catalog) {
        Map<String, String> configs = catalog.getConfigs();
        Preconditions.checkThrow(
                configs.get(JobConstant.JDBC_URL) == null,
                () ->
                        new FlinkJobGenException(
                                String.format("jdbc url is null, catalog: %s", catalog)));
        Preconditions.checkThrow(
                configs.get(JobConstant.JDBC_USERNAME) == null,
                () ->
                        new FlinkJobGenException(
                                String.format("jdbc username is null, catalog: %s", catalog)));
        Preconditions.checkThrow(
                configs.get(JobConstant.JDBC_PASSWORD) == null,
                () ->
                        new FlinkJobGenException(
                                String.format("jdbc password is null, catalog: %s", catalog)));
    }

    private static void checkTidbConfigs(Catalog catalog) {
        Map<String, String> configs = catalog.getConfigs();
        Preconditions.checkThrow(
                configs.get(JobConstant.TIDB_DATABASE_URL) == null,
                () ->
                        new FlinkJobGenException(
                                String.format("jdbc url is null, catalog: %s", catalog)));
        Preconditions.checkThrow(
                configs.get(JobConstant.TIDB_USERNAME) == null,
                () ->
                        new FlinkJobGenException(
                                String.format("jdbc username is null, catalog: %s", catalog)));
        Preconditions.checkThrow(
                configs.get(JobConstant.TIDB_PASSWORD) == null,
                () ->
                        new FlinkJobGenException(
                                String.format("jdbc password is null, catalog: %s", catalog)));
    }
}
