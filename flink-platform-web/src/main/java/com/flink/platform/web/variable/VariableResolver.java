package com.flink.platform.web.variable;

import com.flink.platform.dao.entity.JobRunInfo;

import java.util.Map;

/**
 * Variable resolver.
 */
public interface VariableResolver {

    default boolean supports(JobRunInfo jobRun, String content) {
        return true;
    }

    Map<String, Object> resolve(JobRunInfo jobRun, String content);
}
