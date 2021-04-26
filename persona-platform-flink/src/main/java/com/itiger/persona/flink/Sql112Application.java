package com.itiger.persona.flink;

import com.itiger.persona.common.entity.job.Catalog;
import com.itiger.persona.common.entity.job.Function;
import com.itiger.persona.common.entity.job.SqlContext;
import com.itiger.persona.common.util.JsonUtil;
import com.itiger.persona.flink.common.ConfigLoader;
import com.itiger.persona.flink.helper.Catalogs;
import com.itiger.persona.flink.helper.Configurations;
import com.itiger.persona.flink.helper.ExecuteSqls;
import com.itiger.persona.flink.helper.ExecutionEnvs;
import com.itiger.persona.flink.helper.Functions;
import org.apache.flink.table.api.TableEnvironment;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * must input a valid sql context file
 *
 * @author tiger
 */
public class Sql112Application {

    public static void main(String[] args) throws Exception {
        // deSer sql context
        Path sqlContextPath = Paths.get(args[0]);
        SqlContext sqlContext = JsonUtil.toBean(sqlContextPath, SqlContext.class);

        // step 1: create and configure environment
        TableEnvironment tEnv = ExecutionEnvs.createExecutionEnv(sqlContext);
        Map<String, String> configMap = ConfigLoader.loadDefault();
        configMap.putAll(sqlContext.getConfigs());
        configMap.forEach((key, value) -> Configurations.setConfig(tEnv, key, value));

        // step 2: add catalog
        List<Catalog> catalogs = sqlContext.getCatalogs();
        Catalogs.registerCatalogsToTableEnv(tEnv, catalogs);

        // step 3: create temporary system function
        List<Function> functions = sqlContext.getFunctions();
        Functions.registerFunctionsToTableEnv(tEnv, functions);

        // step 4: exec sql
        ExecuteSqls.execSqls(tEnv, sqlContext.getSqls());
    }

}
