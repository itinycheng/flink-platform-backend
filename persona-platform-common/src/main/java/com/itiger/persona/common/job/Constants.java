package com.itiger.persona.common.job;

import java.util.regex.Pattern;

/**
 * constant values for key
 *
 * @author tiny.wang
 */
public class Constants {

    public static final String JDBC_URL = "jdbc-url";

    public static final String JDBC_USERNAME = "jdbc-username";

    public static final String JDBC_PASSWORD = "jdbc-password";

    public static final String FILTER_PUSH_DOWN = "filter-push-down";

    public final static String SEMICOLON = ";";

    public final static String SQL_LINE_SEPARATOR = "\n";

    public final static String SQL_COMMENT_SYMBOL = "--";

    public static final int SQL_PATTERN_CONFIGS = Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL;

}
