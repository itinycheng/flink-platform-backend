package com.flink.platform.web;

import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.web.enums.Placeholder;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import static com.flink.platform.web.enums.Placeholder.CURRENT_TIMESTAMP;
import static com.flink.platform.web.enums.Placeholder.JOB_RUN;
import static java.util.stream.Collectors.toMap;
import static org.junit.Assert.assertEquals;

/** job runner test. */
public class JobRunnerTest {

    @Test
    public void testEnumJsonSerde() {
        Map<Placeholder, String> sqlVarValueMap = Arrays.stream(new Placeholder[] {CURRENT_TIMESTAMP})
                .map(placeholder ->
                        Pair.of(placeholder, placeholder.provider.apply(null).toString()))
                .collect(toMap(Pair::getLeft, Pair::getRight));
        System.out.println(JsonUtil.toJsonString(sqlVarValueMap));
    }

    @Test
    public void testJobRunPlaceholder() {
        JobRunInfo jobRun = new JobRunInfo();
        jobRun.setId(22L);
        jobRun.setJobId(33L);

        jobRun.setSubject("${ JobRUn:id } wow, ${JobRUn:id}, ${  JobRUn:code  }");
        Map<String, Object> result = JOB_RUN.provider.apply(jobRun);
        result.forEach((s, o) -> jobRun.setSubject(jobRun.getSubject().replace(s, o.toString())));
        assertEquals("22 wow, 22, job_33", jobRun.getSubject());
    }
}
