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

    @org.mockito.Mock
    private com.flink.platform.dao.service.JobFlowRunService jobFlowRunService;

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

    @Test
    public void bizDayResolvesToFlowScheduleTime() {
        var flowRun = new com.flink.platform.dao.entity.JobFlowRun();
        flowRun.setId(100L);
        flowRun.setScheduleTime(java.time.LocalDateTime.of(2024, 6, 21, 23, 59, 59));
        Mockito.when(jobFlowRunService.getById(100L)).thenReturn(flowRun);

        var jobRun = new JobRunInfo();
        jobRun.setFlowRunId(100L);
        jobRun.setSubject("dt=${time:yyyyMMdd[bizDay]}");

        var result = timeVariableResolver.resolve(jobRun, jobRun.getSubject());
        assertEquals("20240621", result.get("${time:yyyyMMdd[bizDay]}"));
    }

    @Test
    public void bizDaySupportsOffset() {
        var flowRun = new com.flink.platform.dao.entity.JobFlowRun();
        flowRun.setId(101L);
        flowRun.setScheduleTime(java.time.LocalDateTime.of(2024, 6, 21, 23, 59, 59));
        Mockito.when(jobFlowRunService.getById(101L)).thenReturn(flowRun);

        var jobRun = new JobRunInfo();
        jobRun.setFlowRunId(101L);
        jobRun.setSubject("dt=${time:yyyyMMdd[bizDay-1d]}");

        var result = timeVariableResolver.resolve(jobRun, jobRun.getSubject());
        assertEquals("20240620", result.get("${time:yyyyMMdd[bizDay-1d]}"));
    }

    @Test
    public void bizUnitsConsistentAcrossMultiplePlaceholders() {
        var flowRun = new com.flink.platform.dao.entity.JobFlowRun();
        flowRun.setId(102L);
        flowRun.setScheduleTime(java.time.LocalDateTime.of(2024, 12, 31, 23, 59, 59));
        Mockito.when(jobFlowRunService.getById(102L)).thenReturn(flowRun);

        var jobRun = new JobRunInfo();
        jobRun.setFlowRunId(102L);
        jobRun.setSubject("y=${time:yyyy[bizYear]} m=${time:yyyyMM[bizMonth]} d=${time:yyyyMMdd[bizDay]}");

        var result = timeVariableResolver.resolve(jobRun, jobRun.getSubject());
        assertEquals("2024", result.get("${time:yyyy[bizYear]}"));
        assertEquals("202412", result.get("${time:yyyyMM[bizMonth]}"));
        assertEquals("20241231", result.get("${time:yyyyMMdd[bizDay]}"));
        Mockito.verify(jobFlowRunService, Mockito.times(1)).getById(102L);
    }

    @Test
    public void bizDayFallsBackToJobRunCreateTimeWhenNotInFlow() {
        var jobRun = new JobRunInfo();
        jobRun.setFlowRunId(null); // ad-hoc
        jobRun.setCreateTime(java.time.LocalDateTime.of(2024, 6, 21, 10, 0, 0));
        jobRun.setSubject("dt=${time:yyyyMMdd[bizDay]}");

        var result = timeVariableResolver.resolve(jobRun, jobRun.getSubject());
        assertEquals("20240621", result.get("${time:yyyyMMdd[bizDay]}"));
        Mockito.verifyNoInteractions(jobFlowRunService);
    }

    @Test
    public void bizDayFallsBackThroughLegacyRowChain() {
        var flowRun = new com.flink.platform.dao.entity.JobFlowRun();
        flowRun.setId(103L);
        flowRun.setScheduleTime(null);
        flowRun.setStartTime(java.time.LocalDateTime.of(2024, 6, 21, 8, 30, 0));
        Mockito.when(jobFlowRunService.getById(103L)).thenReturn(flowRun);

        var jobRun = new JobRunInfo();
        jobRun.setFlowRunId(103L);
        jobRun.setSubject("dt=${time:yyyyMMdd[bizDay]}");

        var result = timeVariableResolver.resolve(jobRun, jobRun.getSubject());
        assertEquals("20240621", result.get("${time:yyyyMMdd[bizDay]}"));
    }

    @Test
    public void bizDayFallsBackToFlowRunCreateTime() {
        // Both scheduleTime and startTime null: only createTime remains in fallback chain.
        var flowRun = new com.flink.platform.dao.entity.JobFlowRun();
        flowRun.setId(104L);
        flowRun.setScheduleTime(null);
        flowRun.setStartTime(null);
        org.springframework.test.util.ReflectionTestUtils.setField(
                flowRun, "createTime", java.time.LocalDateTime.of(2024, 6, 21, 7, 0, 0));
        Mockito.when(jobFlowRunService.getById(104L)).thenReturn(flowRun);

        var jobRun = new JobRunInfo();
        jobRun.setFlowRunId(104L);
        jobRun.setSubject("dt=${time:yyyyMMdd[bizDay]}");

        var result = timeVariableResolver.resolve(jobRun, jobRun.getSubject());
        assertEquals("20240621", result.get("${time:yyyyMMdd[bizDay]}"));
    }

    @Test
    public void curDayDoesNotTriggerFlowRunLookup() {
        var jobRun = new JobRunInfo();
        jobRun.setFlowRunId(200L);
        jobRun.setSubject("dt=${time:yyyyMMdd[curDay]}");

        timeVariableResolver.resolve(jobRun, jobRun.getSubject());
        Mockito.verifyNoInteractions(jobFlowRunService);
    }
}
