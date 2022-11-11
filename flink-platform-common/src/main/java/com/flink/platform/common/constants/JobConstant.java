package com.flink.platform.common.constants;

import java.util.regex.Pattern;

/** constant values for key. */
public class JobConstant {

    public static final Pattern APP_ID_PATTERN =
            Pattern.compile("yarn\\s+application\\s+-kill\\s+(\\S+)");

    public static final Pattern JOB_ID_PATTERN =
            Pattern.compile("Job\\s+has\\s+been\\s+submitted\\s+with\\s+JobID\\s+(\\S+)");

    public static final String HADOOP_USER_NAME = "HADOOP_USER_NAME";

    public static final String YARN_APPLICATION_NAME = "yarn.application.name";

    public static final String YARN_PROVIDED_LIB_DIRS = "yarn.provided.lib.dirs";

    public static final String SQL_LINE_SEPARATOR = "\n";

    public static final String SQL_COMMENT_SYMBOL = "--";

    public static final String JSON_FILE_SUFFIX = "json";

    public static final String TMP_FILE_SUFFIX = "tmp";

    public static final String JOB_CODE_VAR = "${jobCode}";

    public static final String CURRENT_TIMESTAMP_VAR = "${currentTimestamp}";

    public static final String TODAY_YYYY_MM_DD_VAR = "${today_yyyyMMdd}";

    public static final int READ_MAX_ROWS = 1000;

    public static final int SQL_PATTERN_CONFIGS =
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL;

    public static final Pattern SQL_PATTERN = Pattern.compile("\\S+.*?;\\s*$", SQL_PATTERN_CONFIGS);

    public static final Pattern LIMIT_PATTERN =
            Pattern.compile("LIMIT\\s+(?<num1>\\d+)(,\\s*(?<num2>\\d+))?$", SQL_PATTERN_CONFIGS);

    public static final Pattern JOB_RUN_PLACEHOLDER_PATTERN =
            Pattern.compile(
                    "\\$\\{\\s*jobRun:(?<field>\\S+)\\s*}",
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    public static final Pattern HDFS_DOWNLOAD_PATTERN =
            Pattern.compile(
                    "\\$\\{\\s*hdfsResourceDownload:(?<file>\\S+)\\s*}",
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
}
