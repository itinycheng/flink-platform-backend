package com.flink.platform.web.aspect;

import com.flink.platform.common.annotation.Auditable;
import com.flink.platform.common.enums.EntityType;
import com.flink.platform.common.enums.OperationType;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.AuditLog;
import com.flink.platform.dao.entity.Identifiable;
import com.flink.platform.dao.service.AuditLogService;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.common.RequestContext;
import com.flink.platform.web.dto.ResultInfo;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

import static com.flink.platform.common.enums.EntityType.FLOW;
import static com.flink.platform.common.enums.EntityType.FLOW_RUN;
import static com.flink.platform.common.enums.EntityType.JOB;
import static com.flink.platform.common.enums.EntityType.JOB_RUN;
import static com.flink.platform.common.enums.ResponseStatus.SUCCESS;

/**
 * Audits user operations on controller methods annotated with {@link Auditable}.
 *
 * <p>Assumes audited methods are NOT themselves {@code @Transactional}: the business
 * {@code @Transactional} lives in the service layer and commits inside {@code proceed()},
 * so the audit re-read + save run after commit (audit has no shared fate with the business tx).
 */
@Slf4j
@Aspect
@Component
public class AuditAspect {

    private final AuditLogService auditLogService;

    private final Map<EntityType, Function<Long, ? extends Identifiable>> reReaders;

    @Autowired
    public AuditAspect(
            AuditLogService auditLogService,
            JobInfoService jobInfoService,
            JobFlowService jobFlowService,
            JobFlowRunService jobFlowRunService,
            JobRunInfoService jobRunInfoService) {
        this.auditLogService = auditLogService;
        this.reReaders = new EnumMap<>(EntityType.class);
        this.reReaders.put(JOB, jobInfoService::getById);
        this.reReaders.put(FLOW, jobFlowService::getById);
        this.reReaders.put(FLOW_RUN, jobFlowRunService::getById);
        this.reReaders.put(JOB_RUN, jobRunInfoService::getById);
    }

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        Identifiable snapshot = null;
        if (snapshotsBeforeCall(auditable.operation())) {
            try {
                var id = readIdFromArgs(pjp, auditable.type());
                snapshot = reRead(auditable.type(), id);
            } catch (Exception e) {
                log.warn(
                        "Failed to pre-read entity for entityType={}, operation={}",
                        auditable.type(),
                        auditable.operation(),
                        e);
            }
        }

        var result = pjp.proceed();
        var businessFailure = isBusinessFailure(result);
        if (businessFailure && !auditable.auditOnFailure()) {
            return result;
        }

        try {
            if (!snapshotsBeforeCall(auditable.operation())) {
                var id = readIdFromReturnValue(pjp, result);
                snapshot = reRead(auditable.type(), id);
            }

            record(pjp, auditable, snapshot);
        } catch (Exception e) {
            log.warn(
                    "Failed to write audit log for entityType={}, operation={}",
                    auditable.type(),
                    auditable.operation(),
                    e);
        }

        return result;
    }

    static boolean snapshotsBeforeCall(OperationType operation) {
        return switch (operation) {
            case DELETE, KILL -> true;
            case INSERT, UPDATE, SCHEDULE, UNSCHEDULE, RUN -> false;
        };
    }

    private boolean isBusinessFailure(Object result) {
        return result instanceof ResultInfo<?> ri && ri.getCode() != SUCCESS.getCode();
    }

    private void record(ProceedingJoinPoint pjp, Auditable auditable, Identifiable snapshot) {
        if (snapshot == null || snapshot.getId() == null) {
            log.warn(
                    "Skip audit: cannot resolve entity for method={}, entityType={}, operation={}",
                    pjp.getSignature(),
                    auditable.type(),
                    auditable.operation());
            return;
        }

        var auditLog = new AuditLog();
        auditLog.setEntityType(auditable.type());
        auditLog.setOperation(auditable.operation());
        auditLog.setEntityId(snapshot.getId());
        auditLog.setSnapshot(JsonUtil.toJsonString(snapshot));
        auditLog.setOperatorId(RequestContext.getUserId());
        auditLogService.save(auditLog);
    }

    private Identifiable reRead(EntityType type, Long id) {
        if (id == null) {
            return null;
        }

        var reader = reReaders.get(type);
        if (reader == null) {
            log.warn("No re-read function registered for entityType={}", type);
            return null;
        }

        return reader.apply(id);
    }

    private Long readIdFromArgs(ProceedingJoinPoint pjp, EntityType type) {
        var args = pjp.getArgs();
        if (args == null || args.length != 1) {
            log.warn("Skip audit: expected exactly one arg, method={}, entityType={}", pjp.getSignature(), type);
            return null;
        }

        if (args[0] instanceof Long id) {
            return id;
        }

        if (args[0] instanceof Identifiable identifiable) {
            return identifiable.getId();
        }

        return null;
    }

    private Long readIdFromReturnValue(ProceedingJoinPoint pjp, Object result) {
        var data = result instanceof ResultInfo<?> resultInfo ? resultInfo.getData() : result;
        if (data instanceof Long) {
            return (Long) data;
        }

        if (data instanceof Identifiable identifiable) {
            return identifiable.getId();
        }

        log.warn("Cannot resolve entity id from return value, method={}", pjp.getSignature());
        return null;
    }
}
