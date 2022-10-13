package com.flink.platform.common.util;

import com.flink.platform.common.constants.Constant;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;

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
            log.error("get localhost ip address failed", e);
            return Constant.EMPTY;
        }
    }
}
