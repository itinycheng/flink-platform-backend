package com.flink.platform.common.constants;

import com.flink.platform.common.util.FunctionUtil;

import java.net.InetAddress;

/** constant. */
public class Constant {

    public static final String HOST_IP;

    public static final String ROOT_DIR;

    public static final String PATH_SEPARATOR;

    public static final String COMMA = ",";

    public static final String COLON = ":";

    public static final String AND = "&";

    public static final String OR = "|";

    public static final String DOT = ".";

    public static final String STAR = "*";

    public static final String SEMICOLON = ";";

    public static final String SPACE = " ";

    public static final String EMPTY = "";

    public static final String AS = "AS";

    public static final String SINGLE_QUOTE = "'";

    public static final String DOUBLE_QUOTE = "\"";

    public static final String BACK_TICK = "`";

    public static final String UNDERSCORE = "_";

    public static final String BRACKET_LEFT = "(";

    public static final String BRACKET_RIGHT = ")";

    public static final String LINE_SEPARATOR = "\n";

    public static final String EQUAL = "=";

    public static final String SLASH = "/";

    public static final String SESSION_USER = "session.user";

    public static final String FLINK = "FLINK";

    public static final String JAVA = "JAVA";

    public static final String SQL = "SQL";

    public static final String SHELL = "SHELL";

    public static final String CONDITION = "CONDITION";

    public static final String FULL_VERSION = "FULL_VERSION";

    static {
        PATH_SEPARATOR = System.getProperty("path.separator");
        ROOT_DIR = System.getProperty("user.dir");
        HOST_IP = FunctionUtil.getOrDefault(() -> InetAddress.getLocalHost().getHostAddress(), "");
    }
}
