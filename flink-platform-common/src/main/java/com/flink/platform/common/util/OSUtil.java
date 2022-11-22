package com.flink.platform.common.util;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;

import static com.flink.platform.common.constants.Constant.EMPTY;

/** OS utils. */
@Slf4j
public class OSUtil {

    /** Whether is macOS. */
    public static boolean isMacOS() {
        return getOSName().startsWith("Mac");
    }

    /** Whether is windows. */
    public static boolean isWindows() {
        return getOSName().startsWith("Windows");
    }

    /** Get current OS name. */
    public static String getOSName() {
        return System.getProperty("os.name");
    }

    /** Get local host ip. */
    public static String getHostIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.error("Get ip address failed", e);
            return EMPTY;
        }
    }

    /** Get hostname. */
    public static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            log.error("Get hostname failed", e);
            return EMPTY;
        }
    }
}
