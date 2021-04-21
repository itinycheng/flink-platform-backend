package com.itiger.persona.command;

import com.itiger.persona.common.exception.FlinkCommandGenException;
import com.itiger.persona.common.job.SqlContext;
import com.itiger.persona.common.util.JsonUtil;
import com.itiger.persona.entity.JobInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author tiny.wang
 */
@Slf4j
@Component("sqlContextHelper")
public class SqlContextHelper {

    private static final String SQL_FILE_SUFFIX = "json";

    private static final String ROOT_DIR = System.getProperty("user.dir");

    @Value("${flink.sql112.sql-dir}")
    private String sqlDir;

    public String convertFromAndSaveToFile(JobInfo jobInfo) {
        SqlContext sqlContext = convertFrom(jobInfo);
        long timestamp = System.currentTimeMillis();
        String fileName = String.join(".", jobInfo.getCode(), String.valueOf(timestamp), SQL_FILE_SUFFIX);
        return saveToFile(fileName, sqlContext);
    }

    public SqlContext convertFrom(JobInfo jobInfo) {

        return null;
    }

    public String saveToFile(String fileName, SqlContext sqlContext) {
        try {
            String json = JsonUtil.toJsonString(sqlContext);
            String sqlFilePath = String.join("/", ROOT_DIR, sqlDir, fileName);
            Path path = Paths.get(sqlFilePath);
            Files.write(path, json.getBytes(StandardCharsets.UTF_8));
            return sqlFilePath;
        } catch (Exception e) {
            throw new FlinkCommandGenException("serde sql context to local disk failed", e);
        }
    }

}
