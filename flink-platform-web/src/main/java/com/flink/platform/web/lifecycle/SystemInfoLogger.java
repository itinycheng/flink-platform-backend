package com.flink.platform.web.lifecycle;

import com.flink.platform.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * Print system information.
 */
@Slf4j
public class SystemInfoLogger {

    public static void logDetails() {
        try {
            log.info("System Environment Variables:\n {}", JsonUtil.toPrettyJsonString(System.getenv()));
            log.info("System Properties:\n {}", JsonUtil.toPrettyJsonString(System.getProperties()));
        } catch (Exception e) {
            log.error("Failed to print system info", e);
        }
    }
}
