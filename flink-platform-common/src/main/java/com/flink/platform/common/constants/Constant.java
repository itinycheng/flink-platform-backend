package com.flink.platform.common.constants;

import java.io.File;
import java.time.ZoneId;
import java.util.TimeZone;

import static com.flink.platform.common.util.DateUtil.MILLIS_PER_MINUTE;
import static com.flink.platform.common.util.OSUtil.getFirstNoLoopbackIP4Address;
import static com.flink.platform.common.util.OSUtil.getHostname;
import static com.flink.platform.common.util.OSUtil.isWindows;
import static com.flink.platform.common.util.Preconditions.checkNotNull;

/** constant. */
public class Constant {

    public static final long HEARTBEAT_TIMEOUT = 5 * MILLIS_PER_MINUTE;

    public static final String LOCALHOST = "127.0.0.1";

    public static final String HOST_IP;

    public static final String HOSTNAME;

    public static final String USER_DIR;

    public static final String FILE_SEPARATOR = File.separator;

    public static final String COMMA = ",";

    public static final String COLON = ":";

    public static final String AND = "&";

    public static final String OR = "|";

    public static final String DOT = ".";

    public static final String SEMICOLON = ";";

    public static final String SPACE = " ";

    public static final String EMPTY = "";

    public static final String SINGLE_QUOTE = "'";

    public static final String LINE_SEPARATOR = "\n";

    public static final String SLASH = "/";

    public static final String SESSION_USER = "session.user";

    public static final String FLINK = "FLINK";

    public static final String JAVA = "JAVA";

    public static final String SQL = "SQL";

    public static final String SHELL = "SHELL";

    public static final String CONDITION = "CONDITION";

    public static final String DEPENDENT = "DEPENDENT";

    public static final String FULL_VERSION = "FULL_VERSION";

    public static final String PID = isWindows() ? "handle" : "pid";

    public static final TimeZone GLOBAL_TIME_ZONE;

    public static final ZoneId GLOBAL_ZONE_ID;

    public static final String GLOBAL_TIME_ZONE_ID;

    static {
        USER_DIR = System.getProperty("user.dir");
        HOST_IP = checkNotNull(getFirstNoLoopbackIP4Address());
        HOSTNAME = getHostname();

        GLOBAL_TIME_ZONE = TimeZone.getDefault();
        GLOBAL_ZONE_ID = GLOBAL_TIME_ZONE.toZoneId();
        GLOBAL_TIME_ZONE_ID = GLOBAL_TIME_ZONE.getID();
    }
}
