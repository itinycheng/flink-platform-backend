package com.flink.platform.common.exception;

/** flink command generate exception. */
public class JobStatusScrapeException extends RuntimeException {

    public JobStatusScrapeException(String message) {
        super(message);
    }

    public JobStatusScrapeException(String message, Throwable cause) {
        super(message, cause);
    }
}
