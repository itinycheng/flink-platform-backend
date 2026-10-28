package com.flink.platform.web.service;

import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.mapper.JobFlowRunMapper;
import com.flink.platform.dao.service.JobFlowRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
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

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jobFlowRunService, "baseMapper", jobFlowRunMapper);
    }

    @Test
    void waitingRunEndedWithoutExecutingKeepsStartTimeNull() {
        var captor = ArgumentCaptor.forClass(JobFlowRun.class);
        when(jobFlowRunMapper.update(captor.capture(), any())).thenReturn(1);

        assertEquals(1, jobFlowRunService.updateStatusFromAnyTo(1L, Set.of(WAITING), null, FAILURE));

        var updated = captor.getValue();
        assertEquals(FAILURE, updated.getStatus());
        assertNotNull(updated.getEndTime());
        // It never ran; a startTime here would report an hours-long wait as a zero-length run.
        assertNull(updated.getStartTime());
    }

    @Test
    void waitingRunAdvancedToSubmittedIsNotTimestamped() {
        var captor = ArgumentCaptor.forClass(JobFlowRun.class);
        when(jobFlowRunMapper.update(captor.capture(), any())).thenReturn(1);

        assertEquals(1, jobFlowRunService.updateStatusFromAnyTo(1L, Set.of(WAITING), null, SUBMITTED));

        var updated = captor.getValue();
        assertNull(updated.getId());
        assertNull(updated.getStartTime());
        assertNull(updated.getEndTime());
    }
}
