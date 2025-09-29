package com.flink.platform.sql.submit;

import org.apache.flink.configuration.Configuration;

import com.flink.platform.common.enums.SqlType;
import com.flink.platform.common.job.Catalog;
import com.flink.platform.common.job.Function;
import com.flink.platform.common.job.Sql;
import com.flink.platform.common.job.SqlContext;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.sql.submit.base.CommonPath;
import com.flink.platform.sql.submit.base.ConfigLoader;
import com.flink.platform.sql.submit.common.FlinkEnvironment;
import com.flink.platform.sql.submit.helper.Catalogs;
import com.flink.platform.sql.submit.helper.ExecuteSqls;
import com.flink.platform.sql.submit.helper.ExecutionEnvs;
import com.flink.platform.sql.submit.helper.Functions;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/** Used to execute sql of flink 1.15, must be a valid sql context file. */
@Slf4j
public class Sql115Application {
    public static void main(String[] args) throws Exception {
        CommonPath path = CommonPath.parse(args[0]);
        SqlContext sqlContext = JsonUtil.toBean(path.getInputStream(), SqlContext.class);
        log.info("Loaded sqlContext: {}", JsonUtil.toJsonString(sqlContext));

        // step 1: create and configure environment
        Map<String, String> configMap = new HashMap<>();
        configMap.putAll(ConfigLoader.loadDefault(sqlContext.getExecMode()));
        configMap.putAll(sqlContext.getConfigs());
        configMap.putAll(sqlContext.getSqls().stream()
                .filter(sql -> SqlType.SET.equals(sql.getType()))
                .map(Sql::getOperands)
                .collect(toMap(operands -> operands[0], operands -> operands[1])));
        Configuration configuration = Configuration.fromMap(configMap);
        FlinkEnvironment env = ExecutionEnvs.createExecutionEnv(sqlContext.getExecMode(), configuration);

        // step 2: add catalog
        List<Catalog> catalogs = sqlContext.getCatalogs();
        Catalogs.registerCatalogsToTableEnv(env, catalogs);

        // step 3: create temporary system function
        List<Function> functions = sqlContext.getFunctions();
        Functions.registerFunctionsToTableEnv(env, functions);

        // step 4: exec sql
        List<Sql> executableSqls = sqlContext.getSqls().stream()
                .filter(sql -> !SqlType.SET.equals(sql.getType()))
                .collect(toList());
        ExecuteSqls.execSQLs(env, executableSqls);
    }
}
