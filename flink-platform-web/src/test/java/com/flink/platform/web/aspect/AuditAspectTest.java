package com.flink.platform.web.aspect;

import com.flink.platform.common.annotation.Auditable;
import com.flink.platform.common.enums.EntityType;
import com.flink.platform.common.enums.OperationType;
import com.flink.platform.dao.entity.AuditLog;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.service.AuditLogService;
import com.flink.platform.dao.service.JobInfoService;
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

    @InjectMocks
    private AuditAspect auditAspect;

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private Auditable auditable(EntityType type, OperationType op) {
        var ann = mock(Auditable.class);
        lenient().doReturn(type).when(ann).type();
        lenient().doReturn(op).when(ann).operation();
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
}
