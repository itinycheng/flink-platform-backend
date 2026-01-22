package com.flink.platform.web.service;

import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.web.util.ResourceUtil;
import com.flink.platform.web.variable.JobRunVariableResolver;
import com.flink.platform.web.variable.ResourceVariableResolver;
import com.flink.platform.web.variable.TimeVariableResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class VariableResolverTest {

    @InjectMocks
    private TimeVariableResolver timeVariableResolver;

    @InjectMocks
    private JobRunVariableResolver jobRunVariableResolver;

    @InjectMocks
    private ResourceVariableResolver resourceVariableResolver;

    @Test
    public void testTimeResolver() {
        var jobRun = new JobRunInfo();
        jobRun.setSubject(
                "select count() as date_${time:yyyyMMdd[curDay-1d]} from t where time = ${time:yyyyMMdd[curDay-1d]}");
        System.out.println(timeVariableResolver.resolve(jobRun, jobRun.getSubject()));
        jobRun.setSubject("select * from t where t.time = '${time:yyyy-MM-dd HH:mm:ss[curSecond]}'");
        System.out.println(timeVariableResolver.resolve(jobRun, jobRun.getSubject()));
        jobRun.setSubject("select * from t where t.time = '${time:yyyy-MM-dd HH:mm:ss[curYear+12h]}'");
        System.out.println(timeVariableResolver.resolve(jobRun, jobRun.getSubject()));
    }

    @Test
    public void jobRunPlaceholder() {
        var jobRun = new JobRunInfo();
        jobRun.setId(22L);
        jobRun.setJobId(33L);

        jobRun.setSubject("${jobRun:id} wow, ${jobRun:id}, ${jobRun:code}, ${jobRun:job_id}");
        var result = jobRunVariableResolver.resolve(jobRun, jobRun.getSubject());
        result.forEach((s, o) -> jobRun.setSubject(jobRun.getSubject().replace(s, String.valueOf(o))));
        assertEquals("22 wow, 22, job_33, 33", jobRun.getSubject());
    }

    @Test
    public void resourcePlaceholder() {
        var jobRun = new JobRunInfo();
        jobRun.setId(22L);
        jobRun.setJobId(33L);

        try (var mockStatic = Mockito.mockStatic(ResourceUtil.class)) {
            var storagePath = "/storage/path";
            var localPath = "/local/path";
            mockStatic
                    .when(() -> ResourceUtil.getAbsoluteStoragePath(Mockito.any()))
                    .then(mock -> storagePath);
            mockStatic
                    .when(() -> ResourceUtil.copyFromStorageToLocal(Mockito.any()))
                    .then(mock -> localPath);

            jobRun.setSubject("${resource:path}, ${resource:original:path}, ${resource:local:path}");
            var result = resourceVariableResolver.resolve(jobRun, jobRun.getSubject());
            result.forEach((s, o) -> jobRun.setSubject(jobRun.getSubject().replace(s, String.valueOf(o))));
            assertEquals("/storage/path, /storage/path, /local/path", jobRun.getSubject());
        }
    }
}
