package com.flink.platform.web.enums;

import com.flink.platform.common.util.DateUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;

import static com.flink.platform.common.constants.JobConstant.CURRENT_TIMESTAMP_VAR;
import static com.flink.platform.common.constants.JobConstant.HDFS_DOWNLOAD_PATTERN;
import static com.flink.platform.common.constants.JobConstant.JOB_CODE_VAR;
import static com.flink.platform.common.constants.JobConstant.JOB_RUN_PLACEHOLDER_PATTERN;
import static com.flink.platform.common.constants.JobConstant.TODAY_YYYY_MM_DD_VAR;
import static com.flink.platform.common.enums.JobType.SHELL;
import static com.flink.platform.common.util.FunctionUtil.uncheckedFunction;
import static com.flink.platform.web.util.ResourceUtil.copyFromHdfsToLocal;
import static com.flink.platform.web.util.ResourceUtil.getHdfsFilePath;

/** placeholder in subject field of job. */
@Slf4j
public enum Placeholder {

    /** placeholders. */
    JOB_RUN(
            "${jobRun",
            (Object obj) -> {
                JobRunInfo jobRun = ((JobRunInfo) obj);
                Matcher matcher = JOB_RUN_PLACEHOLDER_PATTERN.matcher(jobRun.getSubject());
                Map<String, Object> result = new HashMap<>();
                while (matcher.find()) {
                    String field = matcher.group("field");
                    Object value = null;
                    if ("code".equalsIgnoreCase(field)) {
                        value = jobRun.getJobCode();
                    } else if ("id".equalsIgnoreCase(field)) {
                        value = jobRun.getId();
                    }
                    result.put(matcher.group(), value);
                }
                return result;
            }),

    HDFS_RESOURCE_DOWNLOAD(
            "${hdfsResourceDownload",
            uncheckedFunction(
                    obj -> {
                        JobRunInfo jobRun = (JobRunInfo) obj;
                        Map<String, Object> result = new HashMap<>();
                        Matcher matcher = HDFS_DOWNLOAD_PATTERN.matcher(jobRun.getSubject());
                        while (matcher.find()) {
                            String variable = matcher.group();
                            String filePath = matcher.group("file");
                            if (StringUtils.isBlank(filePath) || !filePath.startsWith("hdfs")) {
                                throw new RuntimeException(
                                        "Hdfs path not found or isn't start with 'hdfs', variable:"
                                                + variable);
                            }

                            String absoluteHdfsPath =
                                    filePath.startsWith("hdfs")
                                            ? filePath
                                            : getHdfsFilePath(filePath, jobRun.getUserId());
                            String localPath = copyFromHdfsToLocal(absoluteHdfsPath);
                            result.put(variable, localPath);

                            // Make the local file readable and executable.
                            // TODO: Should only for executable files.
                            if (SHELL.equals(jobRun.getType())) {
                                try {
                                    Files.setPosixFilePermissions(
                                            Paths.get(localPath),
                                            PosixFilePermissions.fromString("rwxr-xr-x"));
                                } catch (Exception e) {
                                    log.error("Failed to set file permissions", e);
                                }
                            }
                        }
                        return result;
                    })),

    @Deprecated
    JOB_CODE(
            JOB_CODE_VAR,
            (Object obj) -> {
                String jobCode = ((JobRunInfo) obj).getJobCode();
                Map<String, Object> result = new HashMap<>(1);
                result.put(JOB_CODE_VAR, jobCode);
                return result;
            }),

    @Deprecated
    CURRENT_TIMESTAMP(
            CURRENT_TIMESTAMP_VAR,
            (Object obj) -> {
                Map<String, Object> result = new HashMap<>(1);
                result.put(CURRENT_TIMESTAMP_VAR, System.currentTimeMillis());
                return result;
            }),

    @Deprecated
    TODAY_YYYYMMDD(
            TODAY_YYYY_MM_DD_VAR,
            (Object obj) -> {
                Map<String, Object> result = new HashMap<>(1);
                result.put(
                        TODAY_YYYY_MM_DD_VAR,
                        DateUtil.format(System.currentTimeMillis(), "yyyyMMdd"));
                return result;
            });

    public final String wildcard;

    public final Function<Object, Map<String, Object>> provider;

    Placeholder(String wildcard, Function<Object, Map<String, Object>> provider) {
        this.wildcard = wildcard;
        this.provider = provider;
    }
}
