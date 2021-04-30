package com.itiger.persona.command;

import com.itiger.persona.common.entity.job.Catalog;
import com.itiger.persona.common.entity.job.Function;
import com.itiger.persona.common.entity.job.Sql;
import com.itiger.persona.common.entity.job.SqlContext;
import com.itiger.persona.common.enums.SqlType;
import com.itiger.persona.common.exception.FlinkCommandGenException;
import com.itiger.persona.common.util.JsonUtil;
import com.itiger.persona.entity.JobInfo;
import com.itiger.persona.service.ICatalogInfoService;
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

import static com.itiger.persona.common.constants.JobConstant.DOT;
import static com.itiger.persona.common.constants.JobConstant.JSON_FILE_SUFFIX;
import static com.itiger.persona.common.constants.JobConstant.ROOT_DIR;
import static com.itiger.persona.common.constants.JobConstant.SEMICOLON;
import static com.itiger.persona.common.constants.JobConstant.SQL_PATTERN;
import static java.util.stream.Collectors.toList;

/**
 * @author tiny.wang
 */
@Slf4j
@Component("sqlContextHelper")
public class SqlContextHelper {

    @Value("${flink.local.sql-dir}")
    private String sqlDir;

    @Resource
    private ICatalogInfoService catalogInfoService;

    public String convertFromAndSaveToFile(JobInfo jobInfo) {
        SqlContext sqlContext = convertFrom(jobInfo);
        long timestamp = System.currentTimeMillis();
        String fileName = String.join(DOT, jobInfo.getCode(), String.valueOf(timestamp), JSON_FILE_SUFFIX);
        return saveToFile(fileName, sqlContext);
    }

    public SqlContext convertFrom(JobInfo jobInfo) {
        SqlContext sqlContext = new SqlContext();
        sqlContext.setId(jobInfo.getCode());
        sqlContext.setSqls(toSqls(jobInfo.getSubject()));
        sqlContext.setExecMode(jobInfo.getExecMode());
        sqlContext.setExtJars(Collections.emptyList());
        sqlContext.setConfigs(toConfigs(jobInfo.getConfig()));
        sqlContext.setCatalogs(toCatalogs(jobInfo.getCatalogs()));
        sqlContext.setFunctions(toFunctions(jobInfo.getConfig()));
        return sqlContext;
    }

    /**
     * no use
     */
    private List<Function> toFunctions(String jobConfig) {
        return Collections.emptyList();
    }

    private List<Catalog> toCatalogs(String catalogs) {
        return JsonUtil.toList(catalogs).stream()
                .map(Long::parseLong)
                .map(id -> catalogInfoService.getById(id))
                .map(catalogInfo -> new Catalog(catalogInfo.getName(),
                        catalogInfo.getType(),
                        catalogInfo.getDefaultDatabase(),
                        catalogInfo.getConfigPath(),
                        JsonUtil.toStrMap(catalogInfo.getConfigs())))
                .collect(toList());
    }

    private Map<String, String> toConfigs(String jobConfig) {
        return JsonUtil.toStrMap(jobConfig);
    }

    private List<Sql> toSqls(String subject) {
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
            throw new FlinkCommandGenException(
                    String.format("no sql found or parsing failed, subject: %s", subject));
        }
        return sqlList;
    }

    public String saveToFile(String fileName, SqlContext sqlContext) {
        try {
            String json = JsonUtil.toJsonString(sqlContext);
            String sqlFilePath = String.join("/", ROOT_DIR, sqlDir, fileName);
            FileUtils.write(new File(sqlFilePath), json, StandardCharsets.UTF_8);
            log.info("serial sql context to local disk successfully, path: {}, data: {}", sqlFilePath, json);
            return sqlFilePath;
        } catch (Exception e) {
            throw new FlinkCommandGenException("serde sql context to local disk failed", e);
        }
    }

    public static void main(String[] args) {
        Matcher matcher = SQL_PATTERN.matcher("set a =\n b;\nset c = d;\n select * from a where  name = ';';");
        while (matcher.find()) {
            System.out.println("item: " + matcher.group());
        }
    }
}
