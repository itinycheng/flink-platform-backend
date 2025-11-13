package com.flink.platform.web.enums;

import com.flink.platform.common.constants.JobConstant;
import com.flink.platform.common.util.DateUtil;
import com.flink.platform.common.util.DurationUtil;
import com.flink.platform.common.util.FileUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobParamService;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.service.PluginService;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static com.flink.platform.common.constants.JobConstant.APOLLO_CONF_PATTERN;
import static com.flink.platform.common.constants.JobConstant.APOLLO_CONF_PREFIX;
import static com.flink.platform.common.constants.JobConstant.CURRENT_TIMESTAMP_VAR;
import static com.flink.platform.common.constants.JobConstant.JOB_RUN_PLACEHOLDER_PATTERN;
import static com.flink.platform.common.constants.JobConstant.PARAM_FORMAT;
import static com.flink.platform.common.constants.JobConstant.RESOURCE_PATTERN;
import static com.flink.platform.common.constants.JobConstant.TIME_PLACEHOLDER_PATTERN;
import static com.flink.platform.common.constants.JobConstant.TODAY_YYYY_MM_DD_VAR;
import static com.flink.platform.common.enums.JobType.SHELL;
import static com.flink.platform.common.util.FunctionUtil.uncheckedBiFunction;
import static com.flink.platform.common.util.Preconditions.checkNotNull;
import static com.flink.platform.web.util.ResourceUtil.copyFromStorageToLocal;
import static com.flink.platform.web.util.ResourceUtil.getAbsoluteStoragePath;

/** placeholder in subject field of job. */
@Slf4j
public enum Placeholder {
    JOB_RUN("${jobRun", (JobRunInfo jobRun, String content) -> {
        var result = new HashMap<String, Object>();
        var matcher = JOB_RUN_PLACEHOLDER_PATTERN.matcher(content);
        while (matcher.find()) {
            var field = matcher.group("field");
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

    APOLLO(APOLLO_CONF_PREFIX, uncheckedBiFunction((JobRunInfo jobRun, String content) -> {
        var pluginService = SpringContext.getBean(PluginService.class);
        var result = new HashMap<String, Object>();
        var matcher = APOLLO_CONF_PATTERN.matcher(content);
        while (matcher.find()) {
            var paramName = matcher.group();
            var namespace = matcher.group("namespace");
            var key = matcher.group("key");
            if (StringUtils.isNotBlank(namespace) && StringUtils.isNotBlank(key)) {
                result.put(paramName, pluginService.getApolloConfig(namespace, key));
            }
        }
        return result;
    })),

    RESOURCE("${resource", uncheckedBiFunction((JobRunInfo jobRun, String content) -> {
        var result = new HashMap<String, Object>();
        var matcher = RESOURCE_PATTERN.matcher(content);
        while (matcher.find()) {
            var variable = matcher.group();
            var filePath = matcher.group("file");
            if (StringUtils.isBlank(filePath)) {
                throw new RuntimeException("Hdfs path not found, variable:" + variable);
            }

            // filePath.startsWith("hdfs") ? filePath : getStorageFilePath(filePath, jobRun.getUserId());
            var absoluteStoragePath = getAbsoluteStoragePath(filePath);
            var localPath = copyFromStorageToLocal(absoluteStoragePath);
            result.put(variable, localPath);

            // Make the local file readable and executable.
            // TODO: Should only for executable files.
            if (SHELL.equals(jobRun.getType())) {
                try {
                    FileUtil.setPermissions(Path.of(localPath), "rwxr--r--");
                } catch (Exception e) {
                    log.error("Failed to set file permissions", e);
                }
            }
        }
        return result;
    })),

    PARAM("${param", (JobRunInfo jobRun, String content) -> {
        var jobParamService = SpringContext.getBean(JobParamService.class);
        var jobParams = jobParamService.getJobParams(jobRun.getJobId());
        if (CollectionUtils.isEmpty(jobParams)) {
            return Collections.emptyMap();
        }

        var result = new HashMap<String, Object>();
        jobParams.forEach(jobParam -> {
            String param = PARAM_FORMAT.formatted(jobParam.getParamName());
            if (content.contains(param)) {
                result.put(param, jobParam.getParamValue());
            }
        });
        return result;
    }),

    // ${time:yyyyMMdd[curDate-3d]}
    TIME("${time", (JobRunInfo jobRun, String content) -> {
        var result = new HashMap<String, Object>();
        var matcher = TIME_PLACEHOLDER_PATTERN.matcher(content);
        while (matcher.find()) {
            var variable = matcher.group();
            if (result.containsKey(variable)) {
                continue;
            }

            var format = checkNotNull(matcher.group("format"));
            var baseTime = checkNotNull(matcher.group("baseTime"));
            var operator = matcher.group("operator");
            var duration = matcher.group("duration");
            var baseTimeUnit = BaseTimeUnit.of(baseTime);
            var destTime = baseTimeUnit.provider.get();
            // dest time plus duration.
            if (StringUtils.isNotBlank(duration)) {
                var parsedDuration = DurationUtil.parse(duration);
                if ("-".equals(operator)) {
                    parsedDuration = parsedDuration.negated();
                }
                destTime = destTime.plus(parsedDuration);
            }

            result.put(variable, DateUtil.format(destTime, format));
        }
        return result;
    }),

    @Deprecated
    CURRENT_TIMESTAMP(CURRENT_TIMESTAMP_VAR, (JobRunInfo jobRun, String content) -> {
        var result = new HashMap<String, Object>(1);
        result.put(CURRENT_TIMESTAMP_VAR, System.currentTimeMillis());
        return result;
    }),

    @Deprecated
    TODAY_YYYYMMDD(TODAY_YYYY_MM_DD_VAR, (JobRunInfo jobRun, String content) -> {
        var result = new HashMap<String, Object>(1);
        result.put(TODAY_YYYY_MM_DD_VAR, DateUtil.format(System.currentTimeMillis(), "yyyyMMdd"));
        return result;
    });

    public final String wildcard;

    public final BiFunction<JobRunInfo, String, Map<String, Object>> provider;

    Placeholder(@Nonnull String wildcard, @Nonnull BiFunction<JobRunInfo, String, Map<String, Object>> provider) {
        this.wildcard = wildcard;
        this.provider = provider;
    }

    public @Nonnull Map<String, Object> apply(JobRunInfo jobRun, String content) {
        return provider.apply(jobRun, content);
    }

    @Getter
    enum BaseTimeUnit {
        CUR_YEAR(JobConstant.CUR_YEAR, () -> LocalDateTime.of(LocalDate.now(), LocalTime.MIN)
                .withDayOfYear(1)),
        CUR_MONTH(JobConstant.CUR_MONTH, () -> LocalDateTime.of(LocalDate.now(), LocalTime.MIN)
                .withDayOfMonth(1)),
        CUR_DAY(JobConstant.CUR_DAY, () -> LocalDateTime.of(LocalDate.now(), LocalTime.MIN)),
        CUR_HOUR(JobConstant.CUR_HOUR, () -> LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)),
        CUR_MINUTE(JobConstant.CUR_MINUTE, () -> LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)),
        CUR_SECOND(JobConstant.CUR_SECOND, () -> LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)),
        CUR_MILLISECOND(JobConstant.CUR_MILLISECOND, () -> LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));

        private final String name;

        private final Supplier<LocalDateTime> provider;

        BaseTimeUnit(String name, Supplier<LocalDateTime> provider) {
            this.name = name;
            this.provider = provider;
        }

        public static BaseTimeUnit of(@Nonnull String name) {
            return Arrays.stream(values())
                    .filter(baseTimeUnit -> baseTimeUnit.name.equalsIgnoreCase(name))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Could not find BaseTimeUnit by name"));
        }
    }

    public static void main(String[] args) {
        var jobRun = new JobRunInfo();

        jobRun.setSubject(
                "select count() as date_${time:yyyyMMdd[curDay-1d]} from t where time = ${time:yyyyMMdd[curDay-1d]}");
        System.out.println(TIME.provider.apply(jobRun, jobRun.getSubject()));

        jobRun.setSubject("select * from t where t.time = '${time:yyyy-MM-dd HH:mm:ss[curSecond]}'");
        System.out.println(TIME.provider.apply(jobRun, jobRun.getSubject()));

        jobRun.setSubject("select * from t where t.time = '${time:yyyy-MM-dd HH:mm:ss[curYear+12h]}'");
        System.out.println(TIME.provider.apply(jobRun, jobRun.getSubject()));
    }
}
