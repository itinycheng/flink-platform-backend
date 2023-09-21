package com.flink.platform.web.command;

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
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static com.flink.platform.common.constants.Constant.DOT;
import static com.flink.platform.common.constants.Constant.ROOT_DIR;
import static com.flink.platform.common.constants.Constant.SLASH;
import static com.flink.platform.common.constants.JobConstant.JSON_FILE_SUFFIX;
import static com.flink.platform.common.constants.JobConstant.SQL_PATTERN;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;

/** Sql context helper. */
@Slf4j
@Component("sqlContextHelper")
public class SqlContextHelper {

    @Value("${flink.local.sql-dir}")
    private String sqlDir;

    @Resource
    private CatalogInfoService catalogInfoService;

    public String convertFromAndSaveToFile(JobRunInfo jobRun) {
        SqlContext sqlContext = convertFrom(jobRun);
        long timestamp = System.currentTimeMillis();
        String fileName = String.join(DOT, jobRun.getJobCode(), String.valueOf(timestamp), JSON_FILE_SUFFIX);
        return saveToFile(fileName, sqlContext);
    }

    public SqlContext convertFrom(JobRunInfo jobRun) {
        FlinkJob flinkJob = jobRun.getConfig().unwrap(FlinkJob.class);
        SqlContext sqlContext = new SqlContext();
        sqlContext.setId(jobRun.getJobCode());
        sqlContext.setSqls(toSqls(jobRun.getSubject()));
        sqlContext.setExecMode(jobRun.getExecMode());
        sqlContext.setExtJars(flinkJob.getExtJarPaths());
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
        return ListUtils.defaultIfNull(catalogs, emptyList()).stream()
                .map(id -> catalogInfoService.getById(id))
                .map(catalogInfo -> {
                    String createSql = catalogInfo.getCreateSql();
                    for (Map.Entry<String, Object> variable : variables.entrySet()) {
                        createSql = createSql.replace(
                                variable.getKey(), variable.getValue().toString());
                    }

                    return new Catalog(catalogInfo.getName(), catalogInfo.getType(), createSql);
                })
                .collect(toList());
    }

    private Map<String, String> toConfigs(Map<String, String> configs) {
        return configs != null ? configs : emptyMap();
    }

    public List<Sql> toSqls(String subject) {
        List<Sql> sqlList = SqlUtil.convertToSqls(subject);
        if (sqlList.isEmpty()) {
            throw new CommandUnableGenException(String.format("no sql found or parsing failed, subject: %s", subject));
        }
        return sqlList;
    }

    public String saveToFile(String fileName, SqlContext sqlContext) {
        try {
            String json = JsonUtil.toJsonString(sqlContext);
            String sqlFilePath = String.join(SLASH, ROOT_DIR, sqlDir, fileName);
            FileUtils.write(new File(sqlFilePath), json, StandardCharsets.UTF_8);
            log.info("serial sql context to local disk successfully, path: {}, data: {}", sqlFilePath, json);
            return sqlFilePath;
        } catch (Exception e) {
            throw new RuntimeException("serde sql context to local disk failed", e);
        }
    }

    public static void main(String[] args) {
        Matcher matcher = SQL_PATTERN.matcher("set a =\n b;\nset c = d;\n select * from a where  name = ';';");
        while (matcher.find()) {
            System.out.println("item: " + matcher.group());
        }
    }
}
