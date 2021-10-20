package com.flink.platform.web.command;

import com.flink.platform.common.enums.SqlType;
import com.flink.platform.common.exception.JobCommandGenException;
import com.flink.platform.common.job.Catalog;
import com.flink.platform.common.job.Function;
import com.flink.platform.common.job.Sql;
import com.flink.platform.common.job.SqlContext;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.web.entity.JobInfo;
import com.flink.platform.web.service.ICatalogInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static com.flink.platform.common.constants.Constant.DOT;
import static com.flink.platform.common.constants.Constant.SEMICOLON;
import static com.flink.platform.common.constants.Constant.SLASH;
import static com.flink.platform.common.constants.JobConstant.JSON_FILE_SUFFIX;
import static com.flink.platform.common.constants.JobConstant.ROOT_DIR;
import static com.flink.platform.common.constants.JobConstant.SQL_PATTERN;
import static java.util.stream.Collectors.toList;

/** Sql context helper. */
@Slf4j
@Component("sqlContextHelper")
public class SqlContextHelper {

    @Value("${flink.local.sql-dir}")
    private String sqlDir;

    @Resource private ICatalogInfoService catalogInfoService;

    public String convertFromAndSaveToFile(JobInfo jobInfo) {
        SqlContext sqlContext = convertFrom(jobInfo);
        long timestamp = System.currentTimeMillis();
        String fileName =
                String.join(DOT, jobInfo.getCode(), String.valueOf(timestamp), JSON_FILE_SUFFIX);
        return saveToFile(fileName, sqlContext);
    }

    public SqlContext convertFrom(JobInfo jobInfo) {
        SqlContext sqlContext = new SqlContext();
        sqlContext.setId(jobInfo.getCode());
        sqlContext.setSqls(toSqls(jobInfo.getSubject()));
        sqlContext.setExecMode(jobInfo.getExecMode());
        sqlContext.setExtJars(JsonUtil.toList(jobInfo.getExtJars()));
        sqlContext.setConfigs(toConfigs(jobInfo.getConfig()));
        sqlContext.setCatalogs(toCatalogs(jobInfo.getCatalogs()));
        sqlContext.setFunctions(toFunctions(jobInfo.getConfig()));
        return sqlContext;
    }

    /** no use. */
    private List<Function> toFunctions(String jobConfig) {
        return Collections.emptyList();
    }

    private List<Catalog> toCatalogs(String catalogs) {
        return JsonUtil.toList(catalogs).stream()
                .map(Long::parseLong)
                .map(id -> catalogInfoService.getById(id))
                .map(
                        catalogInfo ->
                                new Catalog(
                                        catalogInfo.getName(),
                                        catalogInfo.getType(),
                                        catalogInfo.getDefaultDatabase(),
                                        catalogInfo.getConfigPath(),
                                        JsonUtil.toStrMap(catalogInfo.getConfigs())))
                .collect(toList());
    }

    private Map<String, String> toConfigs(String jobConfig) {
        return JsonUtil.toStrMap(jobConfig);
    }

    public List<Sql> toSqls(String subject) {
        subject = subject.trim();
        if (!subject.endsWith(SEMICOLON)) {
            subject = subject + SEMICOLON;
        }
        List<Sql> sqlList = new ArrayList<>();
        Matcher matcher = SQL_PATTERN.matcher(subject);
        while (matcher.find()) {
            String statement = matcher.group();
            sqlList.add(SqlType.parse(statement));
        }
        if (sqlList.size() == 0) {
            throw new JobCommandGenException(
                    String.format("no sql found or parsing failed, subject: %s", subject));
        }
        return sqlList;
    }

    public String saveToFile(String fileName, SqlContext sqlContext) {
        try {
            String json = JsonUtil.toJsonString(sqlContext);
            String sqlFilePath = String.join(SLASH, ROOT_DIR, sqlDir, fileName);
            FileUtils.write(new File(sqlFilePath), json, StandardCharsets.UTF_8);
            log.info(
                    "serial sql context to local disk successfully, path: {}, data: {}",
                    sqlFilePath,
                    json);
            return sqlFilePath;
        } catch (Exception e) {
            throw new JobCommandGenException("serde sql context to local disk failed", e);
        }
    }

    public static void main(String[] args) {
        Matcher matcher =
                SQL_PATTERN.matcher(
                        "set a =\n b;\nset c = d;\n select * from a where  name = ';';");
        while (matcher.find()) {
            System.out.println("item: " + matcher.group());
        }
    }
}
