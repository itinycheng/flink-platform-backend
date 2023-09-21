package com.flink.platform.sql.submit;

import org.apache.flink.table.api.TableEnvironment;

import com.flink.platform.common.enums.SqlType;
import com.flink.platform.common.job.Catalog;
import com.flink.platform.common.job.Function;
import com.flink.platform.common.job.Sql;
import com.flink.platform.common.job.SqlContext;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.sql.submit.base.ConfigLoader;
import com.flink.platform.sql.submit.helper.Catalogs;
import com.flink.platform.sql.submit.helper.Configurations;
import com.flink.platform.sql.submit.helper.ExecuteSqls;
import com.flink.platform.sql.submit.helper.ExecutionEnvs;
import com.flink.platform.sql.submit.helper.Functions;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/** Must input a context file whose sql is validated. */
public class Sql112Application {

    public static void main(String[] args) throws Exception {
        // deSer sql context
        Path sqlContextPath = Paths.get(args[0]);
        SqlContext sqlContext = JsonUtil.toBean(sqlContextPath, SqlContext.class);

        // step 1: create and configure environment
        TableEnvironment tEnv = ExecutionEnvs.createExecutionEnv(sqlContext.getExecMode());
        Map<String, String> configMap = ConfigLoader.loadDefault(sqlContext.getExecMode());
        configMap.putAll(sqlContext.getConfigs());
        configMap.putAll(sqlContext.getSqls().stream()
                .filter(sql -> SqlType.SET.equals(sql.getType()))
                .map(Sql::getOperands)
                .collect(toMap(operands -> operands[0], operands -> operands[1])));
        configMap.forEach((key, value) -> Configurations.setConfig(tEnv, key, value));

        // step 2: add catalog
        List<Catalog> catalogs = sqlContext.getCatalogs();
        Catalogs.registerCatalogsToTableEnv(tEnv, catalogs);

        // step 3: create temporary system function
        List<Function> functions = sqlContext.getFunctions();
        Functions.registerFunctionsToTableEnv(tEnv, functions);

        // step 4: exec sql
        List<Sql> executableSqls = sqlContext.getSqls().stream()
                .filter(sql -> !SqlType.SET.equals(sql.getType()))
                .collect(toList());
        ExecuteSqls.execSqls(tEnv, executableSqls);
    }
}
