package com.flink.platform.web.aspect;

import com.flink.platform.common.annotation.Auditable;
import com.flink.platform.common.enums.EntityType;
import com.flink.platform.common.enums.OperationType;
import com.flink.platform.dao.entity.AuditLog;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.AuditLogService;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.common.RequestContext;
import com.flink.platform.web.dto.ResultInfo;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private JobInfoService jobInfoService;

    @Mock
    private JobFlowService jobFlowService;

    @Mock
    private JobFlowRunService jobFlowRunService;

    @Mock
    private JobRunInfoService jobRunInfoService;

    @InjectMocks
    private AuditAspect auditAspect;

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private Auditable auditable(EntityType type, OperationType op) {
        return auditable(type, op, false);
    }

    private Auditable auditable(EntityType type, OperationType op, boolean auditOnFailure) {
        var ann = mock(Auditable.class);
        lenient().doReturn(type).when(ann).type();
        lenient().doReturn(op).when(ann).operation();
        lenient().doReturn(auditOnFailure).when(ann).auditOnFailure();
        return ann;
    }

    private JobInfo job(long id) {
        var job = new JobInfo();
        job.setId(id);
        job.setName("job-" + id);
        return job;
    }

    @Test
    void insert_snapshotsReReadRow_afterProceed() throws Throwable {
        RequestContext.set(new RequestContext.Context(7L, 100L));
        var reread = job(42L);
        when(jobInfoService.getById(42L)).thenReturn(reread);

        var pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn(ResultInfo.success(job(42L)));

        var result = auditAspect.audit(pjp, auditable(EntityType.JOB, OperationType.INSERT));

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).save(captor.capture());
        var saved = captor.getValue();
        assertEquals(EntityType.JOB, saved.getEntityType());
        assertEquals(OperationType.INSERT, saved.getOperation());
        assertEquals(42L, saved.getEntityId());
        assertEquals(7L, saved.getOperatorId());
        assertSame(ResultInfo.class, result.getClass());
    }

    @Test
    void delete_snapshotsBeforeProceed_idFromLongArg() throws Throwable {
        RequestContext.set(new RequestContext.Context(7L, 100L));
        when(jobInfoService.getById(42L)).thenReturn(job(42L));

        var pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[] {42L});
        when(pjp.proceed()).thenReturn(ResultInfo.success(true));

        auditAspect.audit(pjp, auditable(EntityType.JOB, OperationType.DELETE));

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).save(captor.capture());
        assertEquals(42L, captor.getValue().getEntityId());
        assertEquals(OperationType.DELETE, captor.getValue().getOperation());
    }

    @Test
    void deletePreReadThrows_stillProceeds_andSkipsAudit() throws Throwable {
        when(jobInfoService.getById(42L)).thenThrow(new RuntimeException("db down"));

        var pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[] {42L});
        var businessResult = ResultInfo.success(true);
        when(pjp.proceed()).thenReturn(businessResult);

        var result = auditAspect.audit(pjp, auditable(EntityType.JOB, OperationType.DELETE));

        verify(pjp).proceed();
        assertSame(businessResult, result);
        verify(auditLogService, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void businessException_writesNoAudit_andPropagates() throws Throwable {
        var pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThrows(
                IllegalStateException.class,
                () -> auditAspect.audit(pjp, auditable(EntityType.JOB, OperationType.INSERT)));
        verify(auditLogService, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void auditSaveFailure_doesNotBreakBusinessCall() throws Throwable {
        RequestContext.set(new RequestContext.Context(7L, 100L));
        when(jobInfoService.getById(42L)).thenReturn(job(42L));
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(auditLogService)
                .save(org.mockito.ArgumentMatchers.any());

        var pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn(ResultInfo.success(job(42L)));

        var result = auditAspect.audit(pjp, auditable(EntityType.JOB, OperationType.INSERT));
        assertSame(ResultInfo.class, result.getClass());
    }

    @Test
    void delete_returnsBusinessFailure_writesNoAudit_returnsResultUnchanged() throws Throwable {
        RequestContext.set(new RequestContext.Context(7L, 100L));
        // Pre-read succeeds (purge-in-flow re-reads the row before proceed), but purge deletes
        // nothing and returns OPERATION_NOT_ALLOWED, so no audit row must be persisted.
        lenient().when(jobInfoService.getById(42L)).thenReturn(job(42L));

        var pjp = mock(ProceedingJoinPoint.class);
        lenient().when(pjp.getArgs()).thenReturn(new Object[] {42L});
        var businessResult = ResultInfo.failure(com.flink.platform.common.enums.ResponseStatus.OPERATION_NOT_ALLOWED);
        when(pjp.proceed()).thenReturn(businessResult);

        var result = auditAspect.audit(pjp, auditable(EntityType.JOB, OperationType.DELETE));

        verify(auditLogService, never()).save(org.mockito.ArgumentMatchers.any());
        assertSame(businessResult, result);
    }

    @Test
    void insert_returnsBusinessFailure_writesNoAudit_returnsResultUnchanged() throws Throwable {
        RequestContext.set(new RequestContext.Context(7L, 100L));
        lenient().when(jobInfoService.getById(42L)).thenReturn(job(42L));

        var pjp = mock(ProceedingJoinPoint.class);
        var businessResult = ResultInfo.failure(
                com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER, "illegal input parameter");
        when(pjp.proceed()).thenReturn(businessResult);

        var result = auditAspect.audit(pjp, auditable(EntityType.JOB, OperationType.INSERT));

        verify(auditLogService, never()).save(org.mockito.ArgumentMatchers.any());
        assertSame(businessResult, result);
    }

    @Test
    void unresolvableId_skipsAudit_returnsResult() throws Throwable {
        var pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[] {});
        when(pjp.proceed()).thenReturn(ResultInfo.success(true));

        var result = auditAspect.audit(pjp, auditable(EntityType.JOB, OperationType.DELETE));

        verify(auditLogService, never()).save(org.mockito.ArgumentMatchers.any());
        assertSame(ResultInfo.class, result.getClass());
    }

    @Test
    void flowUpdate_resolvesIdFromLongReturn_reReadsFlow() throws Throwable {
        RequestContext.set(new RequestContext.Context(7L, 100L));
        var flow = new com.flink.platform.dao.entity.JobFlow();
        flow.setId(55L);
        flow.setName("flow-55");
        when(jobFlowService.getById(55L)).thenReturn(flow);

        var pjp = mock(ProceedingJoinPoint.class);
        // JobFlowController.update returns ResultInfo<Long> (the flow id), not the entity.
        when(pjp.proceed()).thenReturn(ResultInfo.success(55L));

        var result = auditAspect.audit(pjp, auditable(EntityType.FLOW, OperationType.UPDATE));

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).save(captor.capture());
        var saved = captor.getValue();
        assertEquals(EntityType.FLOW, saved.getEntityType());
        assertEquals(OperationType.UPDATE, saved.getOperation());
        assertEquals(55L, saved.getEntityId());
        assertEquals(7L, saved.getOperatorId());
        assertSame(ResultInfo.class, result.getClass());
    }

    @Test
    void flowPurge_snapshotsBeforeProceed_idFromLongArg() throws Throwable {
        RequestContext.set(new RequestContext.Context(7L, 100L));
        var flow = new com.flink.platform.dao.entity.JobFlow();
        flow.setId(55L);
        flow.setName("flow-55");
        when(jobFlowService.getById(55L)).thenReturn(flow);

        var pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[] {55L});
        when(pjp.proceed()).thenReturn(ResultInfo.success(55L));

        auditAspect.audit(pjp, auditable(EntityType.FLOW, OperationType.DELETE));

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).save(captor.capture());
        assertEquals(EntityType.FLOW, captor.getValue().getEntityType());
        assertEquals(55L, captor.getValue().getEntityId());
        assertEquals(OperationType.DELETE, captor.getValue().getOperation());
    }

    @Test
    void multipleLongArgs_isAmbiguous_skipsAudit_returnsResultUnchanged() throws Throwable {
        // Two Long args = ambiguous id source. Rather than silently pick the first, the aspect
        // warns and skips the audit; the business call still returns normally. A guard test keeps
        // such methods out of the codebase, so this is defense in depth.
        RequestContext.set(new RequestContext.Context(7L, 100L));
        lenient()
                .when(jobInfoService.getById(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(job(42L));

        var pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[] {42L, 99L});
        var businessResult = ResultInfo.success(true);
        when(pjp.proceed()).thenReturn(businessResult);

        var result = auditAspect.audit(pjp, auditable(EntityType.JOB, OperationType.DELETE));

        verify(auditLogService, never()).save(org.mockito.ArgumentMatchers.any());
        assertSame(businessResult, result);
    }

    @Test
    void flowRunOnce_resolvesIdFromLongReturn_reReadsFlow() throws Throwable {
        RequestContext.set(new RequestContext.Context(7L, 100L));
        var flow = new com.flink.platform.dao.entity.JobFlow();
        flow.setId(55L);
        flow.setName("flow-55");
        when(jobFlowService.getById(55L)).thenReturn(flow);

        var pjp = mock(ProceedingJoinPoint.class);
        // JobFlowController.runOnce returns ResultInfo<Long> (the flow id, not the run id).
        when(pjp.proceed()).thenReturn(ResultInfo.success(55L));

        auditAspect.audit(pjp, auditable(EntityType.FLOW, OperationType.RUN));

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).save(captor.capture());
        assertEquals(OperationType.RUN, captor.getValue().getOperation());
        assertEquals(55L, captor.getValue().getEntityId());
    }

    @Test
    void flowUnschedule_resolvesIdFromLongReturn_reReadsFlow() throws Throwable {
        RequestContext.set(new RequestContext.Context(7L, 100L));
        var flow = new com.flink.platform.dao.entity.JobFlow();
        flow.setId(55L);
        flow.setStatus(com.flink.platform.common.enums.JobFlowStatus.ONLINE);
        when(jobFlowService.getById(55L)).thenReturn(flow);

        // Post-read: the id comes from the return value, so pjp.getArgs() is deliberately not stubbed —
        // moving UNSCHEDULE back to pre-read makes readIdFromArgs resolve nothing and fails this test.
        var pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn(ResultInfo.success(55L));

        auditAspect.audit(pjp, auditable(EntityType.FLOW, OperationType.UNSCHEDULE));

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).save(captor.capture());
        var saved = captor.getValue();
        assertEquals(OperationType.UNSCHEDULE, saved.getOperation());
        assertEquals(55L, saved.getEntityId());
    }

    @Test
    void flowRunKill_snapshotsPreKillStatus_idFromLongArg() throws Throwable {
        RequestContext.set(new RequestContext.Context(7L, 100L));
        var flowRun = new JobFlowRun();
        flowRun.setId(88L);
        flowRun.setStatus(com.flink.platform.common.enums.ExecutionStatus.RUNNING);
        when(jobFlowRunService.getById(88L)).thenReturn(flowRun);

        var pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[] {88L});
        when(pjp.proceed()).thenReturn(ResultInfo.success(88L));

        auditAspect.audit(pjp, auditable(EntityType.FLOW_RUN, OperationType.KILL));

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).save(captor.capture());
        var saved = captor.getValue();
        assertEquals(EntityType.FLOW_RUN, saved.getEntityType());
        assertEquals(OperationType.KILL, saved.getOperation());
        assertEquals(88L, saved.getEntityId());
        // The point of the audit row is what was killed, not the resulting KILLED status.
        assertTrue(saved.getSnapshot().contains("RUNNING"));
    }

    @Test
    void jobRunKill_idAbsentFromReturnValue_stillAudited_viaPreRead() throws Throwable {
        RequestContext.set(new RequestContext.Context(7L, 100L));
        var jobRun = new JobRunInfo();
        jobRun.setId(99L);
        jobRun.setStatus(com.flink.platform.common.enums.ExecutionStatus.RUNNING);
        when(jobRunInfoService.getById(99L)).thenReturn(jobRun);

        var pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[] {99L});
        // Deliberately a return value with no id in it (JobRunController.kill happens to return
        // ResultInfo<Long> today). Pinning the id-less shape keeps the args pre-read as the
        // guaranteed id source for KILL, so narrowing that return type later cannot silently
        // stop auditing kills.
        when(pjp.proceed()).thenReturn(ResultInfo.success(true));

        auditAspect.audit(pjp, auditable(EntityType.JOB_RUN, OperationType.KILL));

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).save(captor.capture());
        var saved = captor.getValue();
        assertEquals(EntityType.JOB_RUN, saved.getEntityType());
        assertEquals(OperationType.KILL, saved.getOperation());
        assertEquals(99L, saved.getEntityId());
    }

    @Test
    void flowRunKill_auditOnFailure_recordsAuditDespiteBusinessFailure() throws Throwable {
        RequestContext.set(new RequestContext.Context(7L, 100L));
        // KillJobService.killFlowRun flips the run to KILLING before fanning out the remote kills,
        // so a partial failure leaves changed state behind and must still be audited.
        var flowRun = new JobFlowRun();
        flowRun.setId(88L);
        flowRun.setStatus(com.flink.platform.common.enums.ExecutionStatus.RUNNING);
        when(jobFlowRunService.getById(88L)).thenReturn(flowRun);

        var pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[] {88L});
        var businessResult =
                ResultInfo.failure(com.flink.platform.common.enums.ResponseStatus.KILL_FLOW_EXCEPTION_FOUND);
        when(pjp.proceed()).thenReturn(businessResult);

        var result = auditAspect.audit(pjp, auditable(EntityType.FLOW_RUN, OperationType.KILL, true));

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).save(captor.capture());
        var saved = captor.getValue();
        assertEquals(EntityType.FLOW_RUN, saved.getEntityType());
        assertEquals(OperationType.KILL, saved.getOperation());
        assertEquals(88L, saved.getEntityId());
        assertSame(businessResult, result);
    }

    @Test
    void kill_withoutAuditOnFailure_writesNoAuditOnBusinessFailure() throws Throwable {
        RequestContext.set(new RequestContext.Context(7L, 100L));
        var jobRun = new JobRunInfo();
        jobRun.setId(99L);
        lenient().when(jobRunInfoService.getById(99L)).thenReturn(jobRun);

        var pjp = mock(ProceedingJoinPoint.class);
        lenient().when(pjp.getArgs()).thenReturn(new Object[] {99L});
        var businessResult = ResultInfo.failure(com.flink.platform.common.enums.ResponseStatus.NO_RUNNING_JOB_FOUND);
        when(pjp.proceed()).thenReturn(businessResult);

        var result = auditAspect.audit(pjp, auditable(EntityType.JOB_RUN, OperationType.KILL));

        verify(auditLogService, never()).save(org.mockito.ArgumentMatchers.any());
        assertSame(businessResult, result);
    }
}
