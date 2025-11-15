package com.flink.platform.web.service;

import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.mapper.JobFlowRunMapper;
import com.flink.platform.dao.service.JobFlowRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private JobFlowRunService jobFlowRunService;

    @InjectMocks
    private ProcessJobService processJobService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jobFlowRunService, "baseMapper", jobFlowRunMapper);
        ReflectionTestUtils.setField(processJobService, "jobFlowRunService", jobFlowRunService);
    }

    @Test
    void testUpdateSharedVarsInJobFlowRun_WithSetValueVariables() {
        var mockJobFlowRun = new JobFlowRun();
        mockJobFlowRun.setSharedVars(Map.of("result", "failure", "count", List.of("1", "2")));
        when(jobFlowRunMapper.querySharedVarsForUpdate(anyLong())).thenReturn(mockJobFlowRun);
        when(jobFlowRunMapper.updateById(any(JobFlowRun.class))).thenReturn(1);

        var jobRun = new JobRunInfo();
        var variables = new HashMap<String, Object>();
        variables.put("${setValue:result=success}", "success");
        variables.put("${setValue:count=10}", "10");
        variables.put("${setValue:favor=apple}", "apple");
        variables.put("setValue", "should be ignored");
        jobRun.setVariables(variables);
        jobRun.setFlowRunId(1L);
        processJobService.updateSharedVarsInJobFlowRun(jobRun);

        verify(jobFlowRunMapper, times(1)).querySharedVarsForUpdate(anyLong());
        verify(jobFlowRunMapper, times(1)).updateById(any(JobFlowRun.class));
    }
}
