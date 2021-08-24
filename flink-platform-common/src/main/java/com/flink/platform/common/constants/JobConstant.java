package com.flink.platform.common.constants;

import com.flink.platform.common.util.FunctionUtil;

import java.net.InetAddress;
import java.util.regex.Pattern;

/**
 * constant values for key
 *
 * @author tiny.wang
 */
public class JobConstant {

    public static final String HOST_IP = FunctionUtil.getOrDefault(() -> InetAddress.getLocalHost().getHostAddress(), "");

    public static final String ROOT_DIR = System.getProperty("user.dir");

    public static final String LINE_SEPARATOR = System.lineSeparator();

    public static final Pattern APP_ID_PATTERN = Pattern.compile("yarn\\s+application\\s+-kill\\s+(\\S+)");

    public static final Pattern JOB_ID_PATTERN = Pattern.compile("Job\\s+has\\s+been\\s+submitted\\s+with\\s+JobID\\s+(\\S+)");

    public static final String HADOOP_USER_NAME = "HADOOP_USER_NAME";

    public static final String YARN_APPLICATION_NAME = "yarn.application.name";

    public static final String YARN_PROVIDED_LIB_DIRS = "yarn.provided.lib.dirs";

    public static final String JDBC_URL = "jdbc.url";

    public static final String JDBC_USERNAME = "jdbc.username";

    public static final String JDBC_PASSWORD = "jdbc.password";

    public static final String TIDB_DATABASE_URL = "tidb.database.url";

    public static final String TIDB_USERNAME = "tidb.username";

    public static final String TIDB_PASSWORD = "tidb.password";

    public static final String TIDB_FILTER_PUSH_DOWN = "tidb.filter-push-down";

    public final static String SQL_LINE_SEPARATOR = "\n";

    public final static String SQL_COMMENT_SYMBOL = "--";

    public static final String JSON_FILE_SUFFIX = "json";

    public static final String TMP_FILE_SUFFIX = "tmp";

    public static final int SQL_PATTERN_CONFIGS = Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL;

    public static final Pattern SQL_PATTERN = Pattern.compile("\\S+.*?;\\s*$", SQL_PATTERN_CONFIGS);

}
