package com.flink.platform.web.enums;

import com.flink.platform.common.util.DateUtil;
import com.flink.platform.dao.entity.JobRunInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;

import static com.flink.platform.common.constants.JobConstant.CURRENT_TIMESTAMP_VAR;
import static com.flink.platform.common.constants.JobConstant.JOB_CODE_VAR;
import static com.flink.platform.common.constants.JobConstant.JOB_RUN_PLACEHOLDER_PATTERN;
import static com.flink.platform.common.constants.JobConstant.TODAY_YYYY_MM_DD_VAR;

/** sql var. */
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
                        TODAY_YYYY_MM_DD_VAR, DateUtil.format(System.currentTimeMillis(), "yyyyMMdd"));
                return result;
            });

    public final String wildcard;

    public final Function<Object, Map<String, Object>> provider;

    Placeholder(String wildcard, Function<Object, Map<String, Object>> provider) {
        this.wildcard = wildcard;
        this.provider = provider;
    }
}
