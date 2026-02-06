package com.flink.platform.web.variable;

import com.flink.platform.dao.entity.JobRunInfo;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Variable resolver.
 */
public interface VariableResolver {

    default boolean supports(@Nullable JobRunInfo jobRun, String content) {
        return true;
    }

    Map<String, Object> resolve(@Nullable JobRunInfo jobRun, String content);
}
