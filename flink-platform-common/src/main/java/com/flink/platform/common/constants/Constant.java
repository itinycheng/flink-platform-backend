package com.flink.platform.common.constants;

import com.flink.platform.common.util.FunctionUtil;

import java.net.InetAddress;

/** constant. */
public class Constant {

    public static final String HOST_IP =
            FunctionUtil.getOrDefault(() -> InetAddress.getLocalHost().getHostAddress(), "");

    public static final String ROOT_DIR = System.getProperty("user.dir");

    public static final String COMMA = ",";

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
}
