package com.flink.platform.web.command.subflow;

import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.model.FlowRunCreationResult;
import com.flink.platform.web.service.FlowRunTriggerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static com.flink.platform.common.enums.JobFlowStatus.ONLINE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowCommandExecutorTest {

    private static final LocalDateTime PARENT_SCHEDULE_TIME = LocalDateTime.of(2026, 10, 27, 10, 0);

    @Mock
    private JobFlowService jobFlowService;

    @Mock
    private JobFlowRunService jobFlowRunService;

    @Mock
    private JobRunInfoService jobRunService;

    @Mock
    private FlowRunTriggerService triggerService;

    @InjectMocks
    private FlowCommandExecutor executor;

    @Test
    void returnsCreatedChildIdAndStatusUsingParentScheduleTime() {
        var child = runnableChild();
        var run = new JobFlowRun();
        run.setId(42L);
        run.setStatus(WAITING);
        when(jobFlowRunService.getScheduleTimeOrNow(7L)).thenReturn(PARENT_SCHEDULE_TIME);
        when(triggerService.handle(child, PARENT_SCHEDULE_TIME)).thenReturn(FlowRunCreationResult.created(run));

        var callback = executor.execCommand(new FlowCommand(11L, 7L, 10L));

        assertEquals(42L, callback.getFlowRunId());
        assertEquals(WAITING, callback.getStatus());
        verify(triggerService).handle(child, PARENT_SCHEDULE_TIME);
    }

    @Test
    void blockedChildFailsParentJobWithoutChildId() {
        var child = runnableChild();
        var conflict = new JobFlowRun();
        conflict.setId(41L);
        when(jobFlowRunService.getScheduleTimeOrNow(7L)).thenReturn(PARENT_SCHEDULE_TIME);
        when(triggerService.handle(child, PARENT_SCHEDULE_TIME))
                .thenReturn(FlowRunCreationResult.blocked(
                        new JobFlowRun(), conflict, "Duplicate schedule time", java.util.List.of()));

        var callback = executor.execCommand(new FlowCommand(11L, 7L, 10L));

        assertEquals(FAILURE, callback.getStatus());
        assertTrue(callback.getMessage().contains("41"));
    }

    private JobFlow runnableChild() {
        var child = new JobFlow();
        child.setId(10L);
        child.setStatus(ONLINE);
        when(jobFlowService.getById(10L)).thenReturn(child);
        return child;
    }
}
