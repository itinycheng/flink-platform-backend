# JOB Audit — Controller-Layer Aspect Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the `@Auditable` audit aspect from the service layer to the `JobInfoController` HTTP entry points, so all four user operations (create / update / delete / purge) on `JobInfo` are audited with a full re-read snapshot.

**Architecture:** A single `@Around("@annotation(auditable)")` aspect on controller methods. It resolves the entity id via a fixed precedence chain (ResultInfo→Identifiable, then a single `Long` arg, then request-body id), re-reads the full row by id (after `proceed()` for INSERT/UPDATE, before for DELETE), and persists an `AuditLog`. Audit runs outside the business transaction, only on success, and never breaks the business call.

**Tech Stack:** Java 21, Spring AOP (proxy-based, already transitively available), MyBatis-Plus, JUnit 5 + Mockito.

## Global Constraints

- Backward compatible — no breaking changes to existing behavior or old data (project rule).
- No schema migration and no enum changes this phase: `EntityType.JOB`, `OperationType.{INSERT,UPDATE,DELETE}`, and `t_audit_log` already exist and are reused verbatim.
- Tests: JUnit 5 + Mockito, `@ExtendWith(MockitoExtension.class)`, no `@SpringBootTest`, manual `@Mock`/`@InjectMocks` (repo convention).
- Formatting enforced by Spotless (Palantir Java Format) + Checkstyle + NullAway at build time. Run `./mvnw spotless:apply` before committing.
- Commit message style: lowercase `type(scope):` + Capitalized imperative, no trailing period. End commit body with the `Co-Authored-By` trailer.

---

### Task 1: Rewrite `AuditAspect` to controller-layer with id-precedence + re-read snapshot

**Files:**
- Modify (rewrite): `flink-platform-web/src/main/java/com/flink/platform/web/aspect/AuditAspect.java`
- Test: `flink-platform-web/src/test/java/com/flink/platform/web/aspect/AuditAspectTest.java`

**Interfaces:**
- Consumes:
  - `AuditLogService.save(AuditLog)` (from `ServiceImpl`) — persists one row.
  - `JobInfoService.getById(Long) : JobInfo` — re-read for the JOB snapshot.
  - `RequestContext.getUserId() : @Nullable Long` — operator id.
  - `Auditable.type() : EntityType`, `Auditable.operation() : OperationType`.
  - `ResultInfo<T>.getData() : T`; `Identifiable.getId() : Long`.
- Produces:
  - `AuditAspect.audit(ProceedingJoinPoint pjp, Auditable auditable) : Object` — the `@Around` advice; returns the business result unchanged.

The aspect keeps an `EnumMap<EntityType, Function<Long, ? extends Identifiable>>` re-read registry. Phase 1 registers only `JOB → jobInfoService::getById`.

- [ ] **Step 1: Write the failing test**

Create `flink-platform-web/src/test/java/com/flink/platform/web/aspect/AuditAspectTest.java`:

```java
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
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
        doReturn(type).when(ann).type();
        doReturn(op).when(ann).operation();
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
    void businessException_writesNoAudit_andPropagates() throws Throwable {
        var pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThrows(IllegalStateException.class,
                () -> auditAspect.audit(pjp, auditable(EntityType.JOB, OperationType.INSERT)));
        verify(auditLogService, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void auditSaveFailure_doesNotBreakBusinessCall() throws Throwable {
        RequestContext.set(new RequestContext.Context(7L, 100L));
        when(jobInfoService.getById(42L)).thenReturn(job(42L));
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(auditLogService).save(org.mockito.ArgumentMatchers.any());

        var pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn(ResultInfo.success(job(42L)));

        var result = auditAspect.audit(pjp, auditable(EntityType.JOB, OperationType.INSERT));
        assertSame(ResultInfo.class, result.getClass());
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl flink-platform-web -Dtest=AuditAspectTest`
Expected: FAIL — current `AuditAspect` has no re-read registry / precedence chain; compile or assertion errors (e.g. `getById` never invoked, entityId null for the `Long`-arg case).

- [ ] **Step 3: Rewrite the aspect**

Replace the entire contents of `flink-platform-web/src/main/java/com/flink/platform/web/aspect/AuditAspect.java` with:

```java
package com.flink.platform.web.aspect;

import com.flink.platform.common.annotation.Auditable;
import com.flink.platform.common.enums.EntityType;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.AuditLog;
import com.flink.platform.dao.entity.Identifiable;
import com.flink.platform.dao.service.AuditLogService;
import com.flink.platform.dao.service.JobInfoService;
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

import static com.flink.platform.common.enums.EntityType.JOB;
import static com.flink.platform.common.enums.OperationType.DELETE;

/**
 * Audits user operations on controller methods annotated with {@link Auditable}.
 *
 * <p>System paths (scheduler, gRPC, background jobs) never pass through a controller, so
 * "annotate-to-audit" is filter-by-construction. The aspect runs outside the business
 * transaction (outer advice), records only on success, and never breaks the business call.
 */
@Slf4j
@Aspect
@Component
public class AuditAspect {

    private final AuditLogService auditLogService;

    /** entityType -> re-read the full row by id, for the snapshot. */
    private final Map<EntityType, Function<Long, ? extends Identifiable>> reReaders;

    @Autowired
    public AuditAspect(AuditLogService auditLogService, JobInfoService jobInfoService) {
        this.auditLogService = auditLogService;
        this.reReaders = new EnumMap<>(EntityType.class);
        this.reReaders.put(JOB, jobInfoService::getById);
    }

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        // DELETE: capture the row before it is gone; write only after proceed() succeeds.
        Identifiable preDeleteSnapshot = null;
        if (DELETE.equals(auditable.operation())) {
            preDeleteSnapshot = reReadFromArgs(pjp, auditable.type());
        }

        Object result = pjp.proceed();

        try {
            record(pjp, auditable, result, preDeleteSnapshot);
        } catch (Exception e) {
            log.warn(
                    "Failed to write audit log for entityType={}, operation={}",
                    auditable.type(),
                    auditable.operation(),
                    e);
        }

        return result;
    }

    private void record(ProceedingJoinPoint pjp, Auditable auditable, Object result, Identifiable preDeleteSnapshot) {
        Identifiable snapshot;
        if (DELETE.equals(auditable.operation())) {
            snapshot = preDeleteSnapshot;
        } else {
            Long id = resolveEntityId(pjp, result);
            snapshot = id == null ? null : reRead(auditable.type(), id);
        }

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

    /** DELETE: id comes from a single Long arg (path variable). Re-read before proceed(). */
    private Identifiable reReadFromArgs(ProceedingJoinPoint pjp, EntityType type) {
        Long id = firstLongArg(pjp);
        if (id == null) {
            id = firstIdentifiableArgId(pjp);
        }
        return id == null ? null : reRead(type, id);
    }

    private Identifiable reRead(EntityType type, Long id) {
        var reader = reReaders.get(type);
        if (reader == null) {
            log.warn("No re-read function registered for entityType={}", type);
            return null;
        }
        return reader.apply(id);
    }

    /** Precedence: (1) ResultInfo data as Identifiable, (2) single Long arg, (3) request-body id. */
    private Long resolveEntityId(ProceedingJoinPoint pjp, Object result) {
        Object data = result instanceof ResultInfo<?> resultInfo ? resultInfo.getData() : result;
        if (data instanceof Identifiable identifiable && identifiable.getId() != null) {
            return identifiable.getId();
        }
        Long argId = firstLongArg(pjp);
        if (argId != null) {
            return argId;
        }
        return firstIdentifiableArgId(pjp);
    }

    private Long firstLongArg(ProceedingJoinPoint pjp) {
        for (Object arg : pjp.getArgs()) {
            if (arg instanceof Long id) {
                return id;
            }
        }
        return null;
    }

    private Long firstIdentifiableArgId(ProceedingJoinPoint pjp) {
        for (Object arg : pjp.getArgs()) {
            if (arg instanceof Identifiable identifiable && identifiable.getId() != null) {
                return identifiable.getId();
            }
        }
        return null;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl flink-platform-web -Dtest=AuditAspectTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Apply formatting and commit**

```bash
./mvnw spotless:apply -pl flink-platform-web
git add flink-platform-web/src/main/java/com/flink/platform/web/aspect/AuditAspect.java \
        flink-platform-web/src/test/java/com/flink/platform/web/aspect/AuditAspectTest.java
git commit -m "refactor(audit): Rewrite AuditAspect for controller layer with re-read snapshot

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Move `@Auditable` from service to `JobInfoController`

**Files:**
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/controller/JobInfoController.java` (add annotations to `create`, `update`, `delete`, `purge`)
- Modify: `flink-platform-dao/src/main/java/com/flink/platform/dao/service/JobInfoService.java` (remove 3 annotations + now-unused imports)

**Interfaces:**
- Consumes: `AuditAspect.audit(...)` from Task 1 (fires on these annotated methods).
- Produces: no new symbols; behavior change only.

- [ ] **Step 1: Annotate the four controller methods**

In `JobInfoController.java`, add the import:

```java
import com.flink.platform.common.annotation.Auditable;
```

and these static imports (alongside the existing `static` import block):

```java
import static com.flink.platform.common.enums.EntityType.JOB;
import static com.flink.platform.common.enums.OperationType.DELETE;
import static com.flink.platform.common.enums.OperationType.INSERT;
import static com.flink.platform.common.enums.OperationType.UPDATE;
```

Add the annotation directly above each `@PostMapping`/`@GetMapping` (below the existing `@RequirePermission`):

- `create` → `@Auditable(type = JOB, operation = INSERT)`
- `update` → `@Auditable(type = JOB, operation = UPDATE)`
- `delete` → `@Auditable(type = JOB, operation = DELETE)`
- `purge`  → `@Auditable(type = JOB, operation = DELETE)`

Example for `create`:

```java
    @RequirePermission(TASK_EDIT)
    @Auditable(type = JOB, operation = INSERT)
    @PostMapping(value = "/create")
    public ResultInfo<JobInfo> create(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestBody JobInfoRequest jobInfoRequest) {
```

- [ ] **Step 2: Remove the obsolete service-layer annotations**

In `JobInfoService.java`, delete the three `@Auditable(...)` lines on `saveJob`, `updateJob`, and `removeAllById`. Then remove the now-unused imports:

```java
import com.flink.platform.common.annotation.Auditable;
```
and the static imports that become unused (`EntityType.JOB`, `OperationType.DELETE/INSERT/UPDATE`) — keep `toSet` and any still-used ones. (NullAway/Checkstyle will flag unused imports at build; verify none remain.)

- [ ] **Step 3: Verify the whole web + dao build compiles and existing tests pass**

Run: `./mvnw test -pl flink-platform-common,flink-platform-dao,flink-platform-web -am`
Expected: BUILD SUCCESS; `AuditAspectTest` green; no unused-import / Checkstyle failures.

- [ ] **Step 4: Manual verification of audit-on-success + no-double-audit**

This is the acceptance check the unit tests can't cover (real Spring proxy on a controller). Start the app (`dev` profile) and:
1. `POST /jobInfo/create` a job → confirm exactly **one** `t_audit_log` row: `entity_type=JOB, operation=INSERT`, `entity_id` = new job id, `snapshot` is the full row JSON, `operator_id` = caller.
2. `POST /jobInfo/update` → **one** `UPDATE` row (this path was silently unaudited before).
3. `GET /jobInfo/delete/{id}` → **one** `DELETE` row with the pre-delete snapshot (this path was silently unaudited before).
4. `GET /jobInfo/purge/{id}` → **one** `DELETE` row.
5. Trigger a validation failure on create (blank required field) → **no** new audit row.

Confirm no duplicate rows (i.e. the removed service-layer annotations are truly gone).

- [ ] **Step 5: Commit**

```bash
./mvnw spotless:apply -pl flink-platform-web,flink-platform-dao
git add flink-platform-web/src/main/java/com/flink/platform/web/controller/JobInfoController.java \
        flink-platform-dao/src/main/java/com/flink/platform/dao/service/JobInfoService.java
git commit -m "feat(audit): Audit JobInfo create/update/delete/purge at controller layer

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- Aspect rewrite to controller layer with success-only, outside-tx, never-break, precedence chain, re-read snapshot (INSERT/UPDATE after, DELETE before) → Task 1. ✅
- Annotate `create`/`update`/`delete`/`purge`; remove service-layer annotations → Task 2. ✅
- No enum/schema/AuditLog changes this phase → honored (neither task touches them). ✅
- Fixes the update/delete silent-miss bug → Task 2 step 4 explicitly verifies these two. ✅
- Deferred (FLOW, FLOW_RUN, JOB_RUN, STOP/KILL, AuditLogWriter, field-diff) → not in any task. ✅

**Placeholder scan:** No TBD/TODO; all code blocks are complete and compilable.

**Type consistency:** `audit(ProceedingJoinPoint, Auditable)` signature, `getById`, `getData()`, `getId()`, `EntityType.JOB`, `OperationType.{INSERT,UPDATE,DELETE}`, `RequestContext.Context(userId, workspaceId)`, `ResultInfo.success(...)` all match the verified current code.

**Note on precedence for INSERT/UPDATE:** both `JobInfoController.create` and `.update` return `ResultInfo<JobInfo>` (JobInfo implements `Identifiable`), so branch (1) resolves the id directly from the return value; the re-read then fetches the merged row. The `Long`-arg branch (2) is what covers `delete`/`purge`.
