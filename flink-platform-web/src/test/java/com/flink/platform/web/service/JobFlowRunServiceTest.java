package com.flink.platform.web.service;

import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.mapper.JobFlowRunMapper;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.web.variable.SetParamVariableResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.enums.ExecutionStatus.CREATED;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for JobFlowRunService.
 */
@ExtendWith(MockitoExtension.class)
class JobFlowRunServiceTest {

    @Mock
    private JobFlowRunMapper jobFlowRunMapper;

    @InjectMocks
    private SetParamVariableResolver setParamResolver;

    @InjectMocks
    private JobFlowRunService jobFlowRunService;

    @InjectMocks
    private ProcessJobService processJobService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jobFlowRunService, "baseMapper", jobFlowRunMapper);
        ReflectionTestUtils.setField(processJobService, "jobFlowRunService", jobFlowRunService);
        ReflectionTestUtils.setField(processJobService, "setParamResolver", setParamResolver);
    }

    @Test
    void testUpdateParamsInJobFlowRun_WithSetParamVariables() {
        var mockJobFlowRun = new JobFlowRun();
        mockJobFlowRun.setParams(Map.of("result", "failure", "count", List.of("1", "2")));
        when(jobFlowRunMapper.queryParamsForUpdate(anyLong())).thenReturn(mockJobFlowRun);
        when(jobFlowRunMapper.updateById(any(JobFlowRun.class))).thenReturn(1);

        var jobRun = new JobRunInfo();
        var variables = new HashMap<String, Object>();
        variables.put("${setParam:result=success}", "success");
        variables.put("${setParam:count=10}", "10");
        variables.put("${setParam:favor=apple}", "apple");
        variables.put("setParam", "should be ignored");
        jobRun.setParams(variables);
        jobRun.setFlowRunId(1L);
        processJobService.updateParamsInJobFlowRun(jobRun);

        verify(jobFlowRunMapper, times(1)).queryParamsForUpdate(anyLong());
        verify(jobFlowRunMapper, times(1)).updateById(any(JobFlowRun.class));
    }

    @Test
    void waitingRunEndedWithoutExecutingKeepsStartTimeNull() {
        var captor = ArgumentCaptor.forClass(JobFlowRun.class);
        when(jobFlowRunMapper.update(captor.capture(), any())).thenReturn(1);

        assertTrue(jobFlowRunService.updateStatusFromTo(1L, WAITING, FAILURE));

        var updated = captor.getValue();
        assertEquals(FAILURE, updated.getStatus());
        assertNotNull(updated.getEndTime());
        // It never ran; a startTime here would report an hours-long wait as a zero-length run.
        assertNull(updated.getStartTime());
    }

    @Test
    void rejectedCreatedRunIsFinished() {
        var captor = ArgumentCaptor.forClass(JobFlowRun.class);
        when(jobFlowRunMapper.update(captor.capture(), any())).thenReturn(1);

        assertTrue(jobFlowRunService.updateStatusFromTo(1L, CREATED, FAILURE));

        var updated = captor.getValue();
        assertEquals(FAILURE, updated.getStatus());
        assertNotNull(updated.getEndTime());
        assertNull(updated.getStartTime());
    }

    @Test
    void waitingRunAdvancedToSubmittedIsNotTimestamped() {
        var captor = ArgumentCaptor.forClass(JobFlowRun.class);
        when(jobFlowRunMapper.update(captor.capture(), any())).thenReturn(1);

        assertTrue(jobFlowRunService.updateStatusFromTo(1L, WAITING, SUBMITTED));

        var updated = captor.getValue();
        assertNull(updated.getStartTime());
        assertNull(updated.getEndTime());
    }

    @Test
    void waitingRunsAreAdvancedToSubmittedInOneBatch() {
        var captor = ArgumentCaptor.forClass(JobFlowRun.class);
        when(jobFlowRunMapper.update(captor.capture(), any())).thenReturn(2);

        assertEquals(2, jobFlowRunService.updateStatusFromWaiting(List.of(1L, 2L), SUBMITTED));

        var updated = captor.getValue();
        assertEquals(SUBMITTED, updated.getStatus());
        assertNull(updated.getStartTime());
        assertNull(updated.getEndTime());
        verify(jobFlowRunMapper).update(any(JobFlowRun.class), any());
    }
}
