package com.flink.platform.common.constants;

import java.util.regex.Pattern;

/** constant values for key. */
public class JobConstant {

    public static final Pattern APP_ID_PATTERN = Pattern.compile("application_\\d{10,13}_\\d+");

    public static final Pattern JOB_ID_PATTERN =
            Pattern.compile("Job\\s+has\\s+been\\s+submitted\\s+with\\s+JobID\\s+(\\S+)");

    public static final String HADOOP_USER_NAME = "HADOOP_USER_NAME";

    public static final String YARN_APPLICATION_NAME = "yarn.application.name";

    public static final String YARN_APPLICATION_TAG = "yarn.tags";

    public static final String YARN_PROVIDED_LIB_DIRS = "yarn.provided.lib.dirs";

    public static final String SQL_LINE_SEPARATOR = "\n";

    public static final String SQL_COMMENT_SYMBOL = "--";

    public static final String JSON_FILE_SUFFIX = "json";

    public static final String CURRENT_TIMESTAMP_VAR = "${currentTimestamp}";

    public static final String TODAY_YYYY_MM_DD_VAR = "${today_yyyyMMdd}";

    public static final int READ_MAX_ROWS = 1000;

    public static final String CUR_YEAR = "curYear";

    public static final String CUR_MONTH = "curMonth";

    public static final String CUR_DAY = "curDay";

    public static final String CUR_HOUR = "curHour";

    public static final String CUR_MINUTE = "curMinute";

    public static final String CUR_SECOND = "curSecond";

    public static final String CUR_MILLISECOND = "curMillisecond";

    public static final int SQL_PATTERN_CONFIGS = Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL;

    public static final Pattern SQL_PATTERN = Pattern.compile("\\S+.*?;\\s*$", SQL_PATTERN_CONFIGS);

    public static final Pattern LIMIT_PATTERN =
            Pattern.compile("LIMIT\\s+(?<num1>\\d+)(,\\s*(?<num2>\\d+))?$", SQL_PATTERN_CONFIGS);

    // ${time:yyyyMMdd[curDate-3d]}
    public static final Pattern TIME_PLACEHOLDER_PATTERN = Pattern.compile(
            String.format(
                    "\\$\\{\\s*time:(?<format>.+?)\\[(?<baseTime>%s|%s|%s|%s|%s|%s|%s)(?<operator>\\+|-)?(?<duration>\\w+)?\\]\\s*}",
                    CUR_YEAR, CUR_MONTH, CUR_DAY, CUR_HOUR, CUR_MINUTE, CUR_SECOND, CUR_MILLISECOND),
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    // ${jobRun:id}
    public static final Pattern JOB_RUN_PLACEHOLDER_PATTERN =
            Pattern.compile("\\$\\{\\s*jobRun:(?<field>[^}]+)\\s*}", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    // ${resource:hdfs:/path/file}
    public static final Pattern RESOURCE_PATTERN =
            Pattern.compile("\\$\\{\\s*resource:(?<file>[^}]+)\\s*}", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    public static final String PARAM_FORMAT = "${param:%s}";

    public static final String APOLLO_CONF_PREFIX = "${apollo";
    // ${apollo:namespace:key}
    public static final Pattern APOLLO_CONF_PATTERN = Pattern.compile(
            "\\$\\{\\s*apollo:(?<namespace>[^}]+)\\s*:\\s*(?<key>[^}]+)\\s*}",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    public static final String CONFIG = "config";

    public static final String FLOW_RUN_ID = "flow_run_id";

    public static final String JOB_RUN_DIR = "job_run";

    public static final String JOB_DIR_FORMAT = "job_%d";

    public static final String USER_DIR_FORMAT = "user_%d";
}
