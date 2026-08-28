# JOB Audit — Controller-Layer Aspect (Phase 1)

Date: 2026-09-04
Branch: `feature/audit-user-operations`

## Goal

Record an **operation trail of user actions** on `JobInfo` (who created / updated / deleted /
purged which job), persisting a full JSON snapshot per operation into `t_audit_log`.
System-triggered writes (scheduler, gRPC, background jobs) must **not** be audited.

This is **phase 1**, scoped to `EntityType.JOB` only. `FLOW`, `FLOW_RUN`, `JOB_RUN`, and the
`STOP` / `KILL` operations described in `TODO.md` are deferred to later phases.

## Current State (verified in code, corrects the stale TODO baseline)

- `EntityType` = `{ JOB }` only (not `{JOB, FLOW}` as TODO claims).
- `OperationType` = `{ INSERT, UPDATE, DELETE }`.
- `AuditAspect` currently sits at the **service layer**, annotated on
  `JobInfoService.saveJob` / `updateJob` / `removeAllById`. It snapshots the return value
  (or, for DELETE, a synthetic `DeleteContext` wrapping the method args).
- `t_audit_log` table, `AuditLog` entity, `AuditLogService`, `AuditLogMapper`,
  `AuditLogController`, and the `@Auditable` (already `@Target(METHOD)`) annotation all exist.
- `AuditInterceptor` and `AuditLogWriter` do **not** exist (never created).

### Bug this phase fixes

The service-layer annotations are attached to methods the controller does **not** call for
two of four operations, so those operations are silently unaudited today:

| Controller method | Actually calls | Audited today? |
|---|---|---|
| `create` | `jobInfoService.saveJob` | ✅ |
| `update` | `jobFlowService.updateJobAndSyncPrecondition` | ❌ (annotation is on unused `updateJob`) |
| `delete` | `jobInfoService.removeById` | ❌ (annotation is on `removeAllById`) |
| `purge`  | `jobInfoService.removeAllById` | ✅ |

Moving the annotation to the HTTP entry points covers all four regardless of which service
method each controller happens to call.

## Chosen Approach — Aspect on Controller methods

An `@Around` aspect on the user-facing `@RestController` methods. Rationale: system paths
(scheduler / gRPC / internal services) never pass through a Controller, so filtering is
"annotate-to-audit" by construction. Same Spring proxy-based AOP mechanism as today; only the
join point moves from service beans to controller beans.

## Design

### 1. `AuditAspect` rewrite (`flink-platform-web/.../aspect/AuditAspect.java`)

- Pointcut unchanged: `@Around("@annotation(auditable)")`.
- **Success-only:** snapshot/write only after `proceed()` returns normally. On any business
  exception, rethrow and write nothing — "an audit row exists" ⇒ "the operation took effect".
- **Audit never breaks the business call:** the entire audit block is wrapped in try-catch;
  on failure `log.warn` only, never rethrow.
- **entityId precedence chain** (no SpEL, resolved in one place):
  1. return value — unwrap `ResultInfo`, take `data` if it is `Identifiable` → `getId()`
     (covers `create` / `update`, which return `ResultInfo<JobInfo>`).
  2. a single `Long` method argument (covers `delete` / `purge` path variable).
  3. the id of the request-body entity, if it is `Identifiable`.
  - If none resolve: `log.warn` (method + sources tried) and **skip** the audit row; the
    business call still returns normally.
- **Snapshot = re-read the row by id.** Uniform for all ops (avoids half-filled entities and
  missing DB-generated fields):
  - INSERT / UPDATE → re-read **after** `proceed()` (captures generated id + merged state).
  - DELETE → re-read **before** `proceed()` (row is gone afterwards); the audit row is still
    written only after `proceed()` succeeds.
  - Re-read via an `EntityType → re-read function` map inside the aspect. Phase 1 registers
    only `JOB → jobInfoService::getById`. Adding `FLOW` later = one entry.
  - Uses instance-service `getById` (consistent with existing `saveJob`'s `getById` style;
    the codebase has no static `Db.*` precedent).
- `operatorId` from `RequestContext.getUserId()`. Audited methods live behind the
  authenticated HTTP layer, so a logged-in user id is expected; if absent, the row is still
  written with a null `operatorId` (schema allows null) — no hard failure.

### 2. Annotation migration

- Add `@Auditable` to `JobInfoController`:
  - `create`  → `@Auditable(type = JOB, operation = INSERT)`
  - `update`  → `@Auditable(type = JOB, operation = UPDATE)`
  - `delete`  → `@Auditable(type = JOB, operation = DELETE)`
  - `purge`   → `@Auditable(type = JOB, operation = DELETE)`
- Remove the three now-obsolete `@Auditable` annotations from `JobInfoService`
  (`saveJob` / `updateJob` / `removeAllById`) to prevent double auditing.

### 3. Unchanged

`EntityType` / `OperationType` enums, `t_audit_log` schema, `AuditLog` entity,
`AuditLogService` / `AuditLogMapper`, `AuditLogController`. No DB migration in this phase.

## Data Flow

```
HTTP request → LoginInterceptor sets RequestContext(userId, workspaceId)
             → JobInfoController.create/update/delete/purge  [@Auditable]
                 → AuditAspect.around:
                     if DELETE: pre = jobInfoService.getById(id)   // before proceed
                     result = proceed()                             // business tx commits here
                     entityId = resolve(result, args, body)
                     snapshot = (INSERT/UPDATE) getById(entityId) : pre
                     auditLogService.save(new AuditLog(JOB, op, entityId, snapshot, userId))
                 → return result
```

Audit `save` runs **outside** the business `@Transactional` (the aspect is outer advice; the
service tx has already committed inside `proceed()`), so audit and business writes have no
shared fate.

## Testing

Unit tests (JUnit 5 + Mockito, matching repo convention — no `@SpringBootTest`):

- INSERT: mock `getById` returns entity → asserts saved `AuditLog` has JOB/INSERT, correct
  entityId (from `ResultInfo<JobInfo>` data), snapshot JSON, operatorId from a stubbed
  `RequestContext`.
- UPDATE: same, id resolved from returned entity.
- DELETE / purge: pre-read happens before `proceed()`; audit written after; entityId from the
  `Long` path-variable arg.
- Business exception in `proceed()` → no audit row, exception propagates.
- Audit `save` throws → business result still returned, only a warn logged.
- entityId unresolvable → skipped with warn, business result returned.

## Out of Scope (this phase)

- `FLOW` and its cascade audit in `JobFlowService.deleteAllById` (N+1 rows).
- `FLOW_RUN` / `JOB_RUN` entity types and `STOP` / `KILL` operations + enum extensions.
- `AuditLogWriter` extraction (only needed once a hand-written cascade call site exists).
- Field-level before/after diff.
