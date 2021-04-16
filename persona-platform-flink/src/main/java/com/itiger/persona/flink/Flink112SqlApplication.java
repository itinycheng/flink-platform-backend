package com.itiger.persona.flink;

import com.itiger.persona.common.job.Catalog;
import com.itiger.persona.common.job.Function;
import com.itiger.persona.common.job.SqlContext;
import com.itiger.persona.common.util.JsonUtil;
import com.itiger.persona.flink.helper.Catalogs;
import com.itiger.persona.flink.helper.ExecuteSqls;
import com.itiger.persona.flink.helper.ExecutionEnvs;
import com.itiger.persona.flink.helper.Functions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.api.TableEnvironment;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * must input a valid sql context file
 *
 * @author tiger
 */
public class Flink112SqlApplication {

    public static void main(String[] args) throws Exception {
        // deSer sql context
        Path sqlContextPath = Paths.get(args[0]);
        SqlContext sqlContext = JsonUtil.toJson(sqlContextPath, SqlContext.class);

        // step 1: create and configure environment
        TableEnvironment tEnv = ExecutionEnvs.createExecutionEnv(sqlContext);
        Configuration config = tEnv.getConfig().getConfiguration();
        Optional.ofNullable(sqlContext.getConfigs())
                .orElse(Collections.emptyMap())
                .forEach(config::setString);
        // TODO step 2: set special config, such as checkpoint/watermark for streaming env

        // step 3: add catalog
        List<Catalog> catalogs = sqlContext.getCatalogs();
        Catalogs.registerCatalogsToTableEnv(tEnv, catalogs);

        // step 4: create temporary system function
        List<Function> functions = sqlContext.getFunctions();
        Functions.registerFunctionsToTableEnv(tEnv, functions);

        // step 5: exec sql
        ExecuteSqls.execSqls(tEnv, sqlContext.getSqls());
        //TableResult tableResult = statementSet.execute();

        // step 6: wait for return and do callback action

    }

}
