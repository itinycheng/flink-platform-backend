package com.flink.platform.web;

import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.web.enums.Placeholder;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static com.flink.platform.web.enums.Placeholder.CURRENT_TIMESTAMP;
import static com.flink.platform.web.enums.Placeholder.JOB_RUN;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** job runner test. */
class JobRunnerTest {

    @Test
    void enumJsonSerde() {
        Map<Placeholder, String> sqlVarValueMap = Arrays.stream(new Placeholder[] {CURRENT_TIMESTAMP})
                .map(placeholder ->
                        Pair.of(placeholder, placeholder.apply(null, null).toString()))
                .collect(toMap(Pair::getLeft, Pair::getRight));
        System.out.println(JsonUtil.toJsonString(sqlVarValueMap));
    }

    @Test
    void jobRunPlaceholder() {
        JobRunInfo jobRun = new JobRunInfo();
        jobRun.setId(22L);
        jobRun.setJobId(33L);

        jobRun.setSubject("${jobRun:id} wow, ${jobRun:id}, ${jobRun:code}");
        Map<String, Object> result = JOB_RUN.apply(jobRun, jobRun.getSubject());
        result.forEach((s, o) -> jobRun.setSubject(jobRun.getSubject().replace(s, o.toString())));
        assertEquals("22 wow, 22, job_33", jobRun.getSubject());
    }
}
