package com.flink.platform.web.command;

/** build call back info. */
public interface JobCallbackBuilder {

    /**
     * extract app id.
     *
     * @param message job command message
     * @return application id
     */
    String extractApplicationId(String message);

    /**
     * extract job id.
     *
     * @param message job command message
     * @return job id
     */
    String extractJobId(String message);
}
