package com.flink.platform.web.service;

import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.web.util.ResourceUtil;
import com.flink.platform.web.variable.JobRunVariableResolver;
import com.flink.platform.web.variable.ResourceVariableResolver;
import com.flink.platform.web.variable.TimeVariableResolver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class VariableResolverTest {

    @Mock
    private JobFlowRunService jobFlowRunService;

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
        var flowRun = new JobFlowRun();
        flowRun.setId(100L);
        flowRun.setScheduleTime(LocalDateTime.of(2026, 6, 21, 23, 59, 59));
        Mockito.when(jobFlowRunService.getLiteById(100L)).thenReturn(flowRun);

        var time1 = "${time:yyyy-MM-dd HH:mm:ss[bizYear]}";
        var time2 = "${time:yyyy-MM-dd HH:mm:ss[bizMonth]}";
        var time3 = "${time:yyyy-MM-dd HH:mm:ss[bizDay]}";
        var time4 = "${time:yyyy-MM-dd HH:mm:ss[bizHour]}";
        var time5 = "${time:yyyy-MM-dd HH:mm:ss[bizMinute]}";
        var time6 = "${time:yyyy-MM-dd HH:mm:ss[bizSecond]}";
        var time7 = "${time:yyyy-MM-dd HH:mm:ss.SSS[bizMillisecond]}";
        var time8 = "${time:yyyy-MM-dd HH:mm:ss.SSS[bizDay-1ms]}";
        var time9 = "${time:yyyy-MM-dd HH:mm:ss[bizDay-1s]}";
        var time10 = "${time:yyyy-MM-dd HH:mm:ss[bizDay-1m]}";
        var time11 = "${time:yyyy-MM-dd HH:mm:ss[bizDay-1h]}";
        var time12 = "${time:yyyy-MM-dd HH:mm:ss[bizDay-1d]}";
        var jobRun = new JobRunInfo();
        jobRun.setFlowRunId(100L);
        jobRun.setSubject(String.join(
                " ", time1, time2, time3, time4, time5, time6, time7, time8, time9, time10, time11, time12));
        var result = timeVariableResolver.resolve(jobRun, jobRun.getSubject());

        assertEquals("2026-01-01 00:00:00", result.get(time1));
        assertEquals("2026-06-01 00:00:00", result.get(time2));
        assertEquals("2026-06-21 00:00:00", result.get(time3));
        assertEquals("2026-06-21 23:00:00", result.get(time4));
        assertEquals("2026-06-21 23:59:00", result.get(time5));
        assertEquals("2026-06-21 23:59:59", result.get(time6));
        assertEquals("2026-06-21 23:59:59.000", result.get(time7));
        assertEquals("2026-06-20 23:59:59.999", result.get(time8));
        assertEquals("2026-06-20 23:59:59", result.get(time9));
        assertEquals("2026-06-20 23:59:00", result.get(time10));
        assertEquals("2026-06-20 23:00:00", result.get(time11));
        assertEquals("2026-06-20 00:00:00", result.get(time12));
        Mockito.verify(jobFlowRunService, Mockito.times(1)).getLiteById(100L);
    }

    @Test
    public void bizThrowsWhenJobRunHasNoFlowRun() {
        var jobRun = new JobRunInfo();
        jobRun.setFlowRunId(null);
        jobRun.setSubject("dt=${time:yyyyMMdd[bizDay]}");

        Assertions.assertThrows(
                NullPointerException.class, () -> timeVariableResolver.resolve(jobRun, jobRun.getSubject()));
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
