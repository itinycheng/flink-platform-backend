package com.flink.platform.web.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;

/**
 * Print system information.
 */
@Slf4j
public class SystemInfoLogger {

    public static void logDetails() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            log.info("System Environment Variables:\n {}", mapper.writeValueAsString(System.getenv()));
            log.info("System Properties:\n {}", mapper.writeValueAsString(System.getProperties()));
        } catch (Exception e) {
            log.error("Failed to print system info", e);
        }
    }
}
