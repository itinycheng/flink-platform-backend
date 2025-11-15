package com.flink.platform.web.command.flink;

import com.flink.platform.common.exception.CommandUnableGenException;
import com.flink.platform.common.job.Catalog;
import com.flink.platform.common.job.Function;
import com.flink.platform.common.job.Sql;
import com.flink.platform.common.job.SqlContext;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.common.util.SqlUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.FlinkJob;
import com.flink.platform.dao.service.CatalogInfoService;
import com.flink.platform.web.environment.DispatcherService;
import com.flink.platform.web.service.JobRunExtraService;
import com.flink.platform.web.service.StorageService;
import com.flink.platform.web.variable.ApolloVariableResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.constants.JobConstant.JSON_FILE_SUFFIX;
import static com.flink.platform.common.constants.JobConstant.SQL_PATTERN;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;

/** Sql context helper. */
@Slf4j
@Component("sqlContextHelper")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class SqlContextHelper {

    private final CatalogInfoService catalogInfoService;

    private final StorageService storageService;

    private final DispatcherService dispatcherService;

    private final JobRunExtraService jobRunExtraService;

    private final ApolloVariableResolver apolloVariableResolver;

    public String convertFromAndSaveToFile(JobRunInfo jobRun) throws IOException {
        var sqlContext = convertFrom(jobRun);
        var json = JsonUtil.toJsonString(sqlContext);
        // save to storage and execution environment.
        var storageFilePath = jobRunExtraService.buildStoragePath(jobRun, JSON_FILE_SUFFIX);
        storageService.createFile(storageFilePath, json, true);
        log.debug("serial sql context to storage successfully, path: {}", storageFilePath);
        var dispatchedFilePath = dispatcherService.buildLocalEnvFilePath(jobRun, JSON_FILE_SUFFIX);
        dispatcherService.writeToLocalEnv(jobRun.getDeployMode(), dispatchedFilePath, json);
        log.debug("serial sql context to execution environment successfully, path: {}", dispatchedFilePath);
        return dispatchedFilePath;
    }

    public SqlContext convertFrom(JobRunInfo jobRun) {
        var flinkJob = jobRun.getConfig().unwrap(FlinkJob.class);
        var sqlContext = new SqlContext();
        sqlContext.setId(jobRun.getJobCode());
        sqlContext.setSqls(toSqls(jobRun.getSubject()));
        sqlContext.setExecMode(jobRun.getExecMode());
        sqlContext.setConfigs(toConfigs(flinkJob.getConfigs()));
        sqlContext.setCatalogs(toCatalogs(flinkJob.getCatalogs(), jobRun.getVariables()));
        sqlContext.setFunctions(toFunctions());
        return sqlContext;
    }

    /** no use. */
    private List<Function> toFunctions() {
        return emptyList();
    }

    private List<Catalog> toCatalogs(List<Long> catalogs, Map<String, Object> variables) {
        if (CollectionUtils.isEmpty(catalogs)) {
            return emptyList();
        }
        return catalogs.stream()
                .map(catalogInfoService::getById)
                .map(catalogInfo -> {
                    var createSql = catalogInfo.getCreateSql();
                    if (variables != null) {
                        for (var variable : variables.entrySet()) {
                            createSql = createSql.replace(
                                    variable.getKey(), variable.getValue().toString());
                        }
                    }

                    for (var entry :
                            apolloVariableResolver.resolve(null, createSql).entrySet()) {
                        createSql = createSql.replace(
                                entry.getKey(), entry.getValue().toString());
                    }

                    return new Catalog(catalogInfo.getName(), catalogInfo.getType(), createSql);
                })
                .collect(toList());
    }

    private Map<String, String> toConfigs(Map<String, String> configs) {
        return configs != null ? configs : emptyMap();
    }

    public List<Sql> toSqls(String subject) {
        var sqlList = SqlUtil.convertToSqls(subject);
        if (sqlList.isEmpty()) {
            throw new CommandUnableGenException("no sql found or parsing failed, subject: %s".formatted(subject));
        }
        return sqlList;
    }

    public static void main(String[] args) {
        var matcher = SQL_PATTERN.matcher("set a =\n b;\nset c = d;\n select * from a where  name = ';';");
        while (matcher.find()) {
            System.out.println("item: " + matcher.group());
        }
    }
}
