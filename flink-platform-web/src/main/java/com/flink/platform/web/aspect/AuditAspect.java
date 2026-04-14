package com.flink.platform.web.aspect;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flink.platform.common.annotation.Auditable;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.AuditLog;
import com.flink.platform.dao.entity.Identifiable;
import com.flink.platform.dao.service.AuditLogService;
import com.flink.platform.web.common.RequestContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.flink.platform.common.enums.OperationType.DELETE;

/** Intercepts @Auditable service methods and persists a full entity snapshot. */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AuditAspect {

    private final AuditLogService auditLogService;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        Object result = pjp.proceed();

        try {
            var entity = DELETE.equals(auditable.operation()) ? buildDeleteContext(pjp) : result;
            saveAuditLog(entity, auditable);
        } catch (Exception e) {
            log.warn(
                    "Failed to save audit log for entityType={}, operation={}",
                    auditable.type(),
                    auditable.operation(),
                    e);
        }

        return result;
    }

    private void saveAuditLog(Object entity, Auditable auditable) {
        var auditLog = new AuditLog();
        auditLog.setEntityType(auditable.type());
        auditLog.setOperation(auditable.operation());
        auditLog.setEntityId(extractEntityId(entity));
        auditLog.setSnapshot(JsonUtil.toJsonString(entity));
        auditLog.setOperatorId(RequestContext.getUserId());
        auditLogService.save(auditLog);
    }

    private DeleteContext buildDeleteContext(ProceedingJoinPoint pjp) {
        var delete = new DeleteContext();
        delete.setArgs(pjp.getArgs());
        delete.setCallee(pjp.getSignature().toString());
        return delete;
    }

    private Long extractEntityId(Object entity) {
        if (entity instanceof Identifiable identifiable) {
            return identifiable.getId();
        }

        log.warn("Entity does not implement Identifiable: {}", entity == null ? null : entity.getClass());
        return null;
    }

    @Data
    static class DeleteContext implements Identifiable {

        private Object[] args;
        private String callee;

        @JsonIgnore
        @Override
        public Long getId() {
            if (args == null || args.length != 1) {
                return null;
            }
            if (args[0] instanceof Long id) {
                return id;
            }
            if (args[0] instanceof Identifiable id) {
                return id.getId();
            }
            return null;
        }
    }
}
