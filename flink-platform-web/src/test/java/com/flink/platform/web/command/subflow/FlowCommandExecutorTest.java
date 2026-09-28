package com.flink.platform.web.command.subflow;

import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.quartz.QuartzService;
import com.flink.platform.web.service.JobFlowRunBizService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static com.flink.platform.common.constants.JobConstant.CONFIG;
import static com.flink.platform.common.constants.JobConstant.SCHEDULE_TIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowCommandExecutorTest {

    private static final LocalDateTime FIRE_TIME = LocalDateTime.of(2026, 9, 28, 10, 0);

    @Mock
    private QuartzService quartzService;

    @Mock
    private JobFlowService jobFlowService;

    @Mock
    private JobFlowRunService jobFlowRunService;

    @Mock
    private JobFlowRunBizService flowRunBizService;

    @Mock
    private JobRunInfoService jobRunService;

    @Captor
    private ArgumentCaptor<JobFlowRun> flowRunCaptor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> dataCaptor;

    @InjectMocks
    private FlowCommandExecutor executor;

    @Test
    void transactionReservesSubflowRunBeforeSchedulingIt() {
        var subflow = new JobFlow();
        subflow.setId(10L);
        subflow.setConfig(new ExecutionConfig());
        when(jobFlowRunService.save(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, JobFlowRun.class).setId(7L);
            return true;
        });
        doReturn(true).when(quartzService).runFlowOnce(same(subflow), any());

        var flowRunId = executor.runOnceInTransaction(subflow, new ExecutionConfig(), FIRE_TIME);

        assertEquals(7L, flowRunId);
        verify(jobFlowService).lockForUpdate(10L);
        verify(jobFlowRunService).save(flowRunCaptor.capture());
        assertEquals(FIRE_TIME, flowRunCaptor.getValue().getScheduleTime());
        verify(quartzService).runFlowOnce(same(subflow), dataCaptor.capture());
        var data = dataCaptor.getValue();
        assertEquals("2026-09-28 10:00:00", data.get(SCHEDULE_TIME));
        assertFalse(String.valueOf(data.get(CONFIG)).contains("scheduleTime"));
    }
}
