package com.flink.platform.web.variable;

import com.flink.platform.common.util.DateUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Order(100)
@Component
public class LegacyVariableResolver implements VariableResolver {

    private static final String CURRENT_TIMESTAMP_VAR = "${currentTimestamp}";

    private static final String TODAY_YYYY_MM_DD_VAR = "${today_yyyyMMdd}";

    @Override
    public Map<String, Object> resolve(JobRunInfo jobRun, String content) {
        var result = new HashMap<String, Object>(2);
        if (content.contains(CURRENT_TIMESTAMP_VAR)) {
            result.put(CURRENT_TIMESTAMP_VAR, System.currentTimeMillis());
        }

        if (content.contains(TODAY_YYYY_MM_DD_VAR)) {
            result.put(TODAY_YYYY_MM_DD_VAR, DateUtil.format(System.currentTimeMillis(), "yyyyMMdd"));
        }
        return result;
    }
}
