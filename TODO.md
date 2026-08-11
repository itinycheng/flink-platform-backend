# TODO

## Execution Log Archival (`t_job_run` / `t_job_flow_run`)

Keep the hot tables small by moving aged rows into monthly-partitioned archive tables, so
operational queries stay fast as execution history grows over years. MySQL-only, no new
storage dependency.

See design: [docs/execution-log-archival.md](docs/execution-log-archival.md)

- [ ] DDL: `t_job_run_archive` / `t_job_flow_run_archive` with monthly RANGE partitions
- [ ] Archive job: batched move from hot to archive, transactional, idempotent on restart
- [ ] Partition maintenance job: provision next month's partition, evict partitions past
      `archive-retention-months`
- [ ] Config binding under `flink-platform.archive.*` (hot/archive retention, cron, batch size,
      per-table overrides, `mode: archive | delete`)
- [ ] Redirect dashboard/analytics queries (`countJobRunGroupByStatus`,
      `countJobFlowRunGroupByStatus`, date-range endpoints) to the archive table
- [ ] Backfill procedure documented for existing deployments
- [ ] (Later) `JobRunArchiver` SPI for pluggable external targets (ClickHouse, S3, ...)

---

## Cross-Medium File Dispatch (HDFS ↔ S3)

`EnvironmentFileService` currently assumes **storage and the active dispatch environment
are on the same medium**. `EnvironmentFileAdapter.buildTempPath` derives tmp paths from
`storageService.getRootPath()` and fail-fasts when scheme mismatches. This works for
single-medium deployments but blocks legitimate hybrid setups:

- **MinIO + HDFS in the same machine room** — both deployed locally; want platform to
  dispatch to whichever medium the job needs (YARN session → HDFS, Flink-on-K8s → S3).
- **Hybrid cloud (machine room HDFS + AWS node)** — a single scheduler cluster spanning
  on-prem and cloud; AWS nodes ideally prefer S3 over HDFS-over-VPN.
- **Heterogeneous job mix** — storage on HDFS for reliability, but specific jobs read /
  write S3-compatible buckets via `s3a://`.

### What's Needed

- [ ] **Path-scheme-based routing** in `EnvironmentFileService.copyIfChanged` /
      `writeToFilePath`: pick adapter by URI scheme of the target path, falling back to
      `@Order` only when scheme is absent.
- [ ] **`buildTempPath(EnvironmentType, segments...)`** overload that lets caller specify
      medium explicitly; `DispatcherService` chooses by deploy mode (YARN_SESSION → HDFS,
      future K8S_S3 → S3, ...).
- [ ] **Per-call `onPrimaryCluster` check** instead of cached state: takes the call's
      target path scheme into account so cross-medium copies aren't incorrectly skipped.
- [ ] **Independent S3 tmp config** (e.g., `environment.s3.tmp-uri`) for the case where
      storage is on HDFS but dispatch needs to land on S3 — current "derive from storage
      rootPath" approach has no source of S3 bucket info in that scenario.
- [ ] (Optional) **Per-node adapter priority override** (env var or property) so AWS nodes
      can prefer S3 while machine-room nodes prefer HDFS within the same scheduler cluster.

### Out of Scope (for now)

- Acted on if/when a real user needs hybrid-medium dispatch. Single-medium deployments
  work fine with the current strict abstraction.

---

## Primary Cluster Marker — Stale `.main_cluster_id` After Switching

`StorageConfig.primaryClusterIdFilePath` writes a random-UUID marker file under the storage
root when the file is missing. `EnvironmentFileAdapter.checkOnPrimaryCluster` (e.g.
`S3FileAdapter`) only checks **whether the file exists**, never reads its content. This
means the marker validates *existence*, not *ownership*.

### Problem

- Switching primary cluster (DR failover, migration to a new bucket/HDFS, swapping storage
  endpoints) leaves the old marker file in place. Any new node mounting the same storage
  finds the file and decides it is "on the primary cluster" — even when it isn't.
- A demoted/old primary brought back online sees its old marker and resumes acting as
  primary → split-brain risk: two clusters writing job artifacts as if they own the
  storage.
- `if (!exists) createFile(UUID.randomUUID())` only writes on first boot; the UUID inside
  is never compared, so it serves no functional purpose today.

### Possible Solutions (to choose later)

1. **Explicit primary cluster ID via config** — declare `storage.primary-cluster-id` in
   yaml/Apollo/env. Marker file is overwritten on every startup with this ID; check
   compares file content against the configured ID. Switching primary = update one config
   value, no manual file deletion.
2. **Derive cluster ID from the storage's canonical location** — hash of
   `${type}://${endpoint}/${bucket}`. Same physical storage → same ID; changing storage
   automatically yields a new ID and obsoletes the old marker.
3. **Keep UUID + add content comparison + provide a CLI/admin endpoint** to manage
   (validate / delete / recreate) the marker. Documented runbook step for primary
   switches. Smallest change but still relies on operator discipline.
4. **Drop the file entirely; use existing distributed coordination** (MySQL ShedLock or a
   new ZK/etcd dependency) for primary election. Marker file becomes informational only.

### Notes

- Recommended starting point: solution 1 + content comparison from solution 3.
- Today's behavior is "first cluster to boot wins forever" — works only for green-field
  deployments that never change storage. Any real ops scenario (failover, migration,
  multi-region) breaks silently.

---

## Full Multi-Tenant Rollout — SQL-level Tenant Filter + TenantContext

> Chosen approach over the `WorkspaceScopedService` base-class idea (first section): enforce
> isolation at the **SQL layer** via MyBatis-Plus `TenantLineInnerInterceptor`, so `getById` /
> `updateById` / `removeById` / custom queries are all auto-scoped without touching services or
> mappers. This section is the plan to grow the current single-table pilot into full coverage.

### Already implemented (pilot, scoped to `t_tag`)

- `WorkspaceScoped` marker interface (`flink-platform-dao/.../entity/WorkspaceScoped.java`),
  declares `Long getWorkspaceId()` as a compile-time contract.
- `TagInfo implements WorkspaceScoped`.
- `WorkspaceTenantLineHandler` — scoped tables derived at runtime from all `WorkspaceScoped`
  entities via `TableInfoHelper` (no hardcoded table name; rename-safe). Lazily built + cached.
  `ignoreTable` short-circuits when no workspace context (background threads unaffected).
- Registered in `MybatisPlusConfig` (tenant interceptor before pagination).
- `WorkspaceScopedValidator` (`ApplicationRunner`) — fail-fast at boot if any `WorkspaceScoped`
  entity lacks a mapped `workspace_id` column.
- Tests: `WorkspaceTenantLineHandlerTest` (6, incl. real SQL-rewrite), `WorkspaceScopedValidatorTest` (2).
- Demo (reference only, delete when done): `RequestContextDemoTest` — shows runAs / runWithoutTenant.

### Design decisions locked in

- **Identify scoped tables by marker interface**, not annotation (avoids confusion with the
  existing `@WorkspaceOptional` method annotation) and not by `workspace_id`-column-detection
  (that would be opt-out / all-tables-at-once; we want explicit opt-in during rollout).
- **Authorization vs isolation are separate**: `PermissionInterceptor` already blocks a forged
  `X-Workspace-Id` for `@RequirePermission` endpoints (membership check). The tenant filter covers
  the *resource-level* IDOR (a legit member fetching another workspace's row by id). Do NOT
  re-add a membership check in `LoginInterceptor` — it was tried and reverted as redundant.

### The core problem to solve for full rollout

`workspace_id` only exists on HTTP threads (`RequestContext` set by `LoginInterceptor`).
Background/internal threads have none, so today they read **across all workspaces** (fail-open).
Fix = generalize `RequestContext` → `RequestContext` that any entry point populates, with two
explicit scopes: `runAs(workspaceId, ...)` and `runWithoutTenant(...)`.

### Work inventory (~10 focused sites, 0 business-logic changes)

**Thread propagation — THE key lever, 1 file:**
- [ ] `ThreadUtil` — decorate task submission to capture the submitting thread's `RequestContext`
      and restore it in the worker thread. All pools go through `ThreadUtil.new*`
      (`FlowExecuteThread`, `JobExecuteThread` virtual, gRPC executor, `CommandMonitor`,
      `ReactiveService`, `WorkerHeartbeat`), so this one change covers the whole
      `Quartz → FlowExecuteThread → JobExecuteThread` chain.

**Generalize context:**
- [ ] `RequestContext` → `RequestContext` (keep HTTP behavior identical; add `runAs` /
      `runWithoutTenant`). Point `WorkspaceTenantLineHandler` at `RequestContext`.

**Entry points — set context (`runAs`), workspace comes from domain object:**
- [ ] `LoginInterceptor` — already sets it (HTTP header). Just switch to `RequestContext`.
- [ ] `JobFlowRunner.execute` (Quartz) — `runAs(jobFlow.getWorkspaceId())`.
- [ ] `FlowExecuteThread.run` — `runAs(jobFlowRun.getWorkspaceId())`.
- [ ] `JobGrpcServer` (`processJob` / `killJob` / `savepointJob` / `getJobStatus`) — load
      JobRun by `jobRunId`, then `runAs(workspaceId)`.
- [ ] `InitJobFlowScheduler` recovery — per-flow `runAs` (or `runWithoutTenant` if only touching
      non-scoped tables).

**Entry points — explicit global scans (`runWithoutTenant`):**
- [ ] `JobFlowScheduleService` (@Scheduled), cron `JobsInJobListStatusChecker` /
      `UnscheduledJobFlowChecker`, `CommandMonitor` — only if they touch scoped tables.

**Optional cleanup (net code reduction):**
- [ ] Remove now-redundant `setWorkspaceId(requireWorkspaceId())` and
      `.eq(workspaceId, requireWorkspaceId())` from ~10 controllers (interceptor does it).

**Policy decision (later):**
- [ ] Decide whether "no context on a scoped-table query" stays fail-open (current) or becomes
      fail-closed (throw, forcing every background path to declare `runAs` / `runWithoutTenant`).

### Add tables to scope (each is one line, handler never changes)

- [ ] `AlertInfo` / `CatalogInfo` / `Datasource` / `Resource` / `JobParam` / `JobFlow` —
      `implements WorkspaceScoped` (validator confirms each has a `workspace_id` column at boot).

### Known limits (SQL-layer approach)

- Raw / hand-written SQL and complex JOINs may not be rewritten reliably — audit needed.
- Guarantees MP-mapped column, not the physical DB column (schema/migration owns that).
- Unique constraints that must be per-workspace need `workspace_id` in the composite index.

---

## Record Who Stops / Kills a Run (Audit)

Manual **run** now records the executor: `JobFlowRunner.execute` reads `USER_ID` from the Quartz
data map and stamps it onto `t_job_flow_run.userId` (falls back to the flow creator for scheduled
fires and sub-flows). **Stop / kill still records nobody** — the three entry points below capture
no operator:

- `JobFlowController.stop(flowId)` — stops scheduling.
- `JobFlowRunController.kill(flowRunId)` — kills a running flow.
- `JobRunController.kill(runId)` — kills a single running job.

### Chosen approach — reuse the existing audit system (no new run-table columns)

The `@Auditable` annotation can't be reused directly here: it treats the method **return value** as
the entity snapshot, but these endpoints return `Long` / `Boolean`. So call `AuditLogService.save(...)`
explicitly from each entry point. `operatorId` is available via `RequestContext.getUserId()` (set by
`LoginInterceptor` on every HTTP request — no new controller params needed).

- [ ] `OperationType` — add `STOP` (and/or `KILL`). Update the `AuditLog` / annotation Javadoc that
      currently says "INSERT / UPDATE / DELETE".
- [ ] `EntityType` — add `JOB_FLOW` / `JOB_FLOW_RUN` / `JOB_RUN` (currently only `JOB`).
- [ ] In the three entry points: build an `AuditLog` (entityType, STOP, entityId = flow/run id,
      snapshot = the run entity, operatorId = `RequestContext.getUserId()`) and `auditLogService.save(...)`.
- [ ] Wrap the save in try/catch + log-warn on failure, mirroring `AuditAspect` (audit must never
      break the kill/stop action itself).
- [ ] (Optional) surface these entries in the existing audit-log UI / `AuditLogController`.

### Notes

- No schema migration on `t_job_run` / `t_job_flow_run`; only enum extensions + `t_audit_log` rows.
- Alternative considered and rejected for now: a plain `log.info("... killed by {}", userId)` line —
  zero schema cost but not queryable and lost on log rotation. Fine only for pure debugging.

---

## Cross-Timezone Scheduling — Per-Job Timezone (Layered Default)

### Problem

Timezone is **implicit and globally singular** today. `Constant.GLOBAL_TIME_ZONE =
TimeZone.getDefault()` is the only source; everything (`DateUtil`, variable interpolation,
Quartz) funnels through it. There is **no timezone field** on any job/flow, and Quartz
triggers are built **without** `.inTimeZone(...)`, so they capture the JVM default at
creation time and freeze it into the Quartz JDBC store.

Consequences for a system deployed across China and the US:

- **Same cron fires at different absolute instants** depending on each node's JVM default
  timezone; containers default to UTC, not the host region.
- **A single deployment cannot serve multiple timezones.** With region-isolated deployments
  (separate DBs per region) this is fine, but a single-deployment setup running
  globally-distributed jobs cannot express "this job runs on Beijing time, that one on New
  York time".
- **`${time:...}` interpolation** (`TimeVariableResolver`, `cur*` uses `LocalDate.now()` /
  `LocalDateTime.now()` with no zone) resolves the wrong "today" across the date boundary —
  a data-correctness bug (wrong partition), not just a display issue.
- **DST**: only a *named* zone (`America/New_York`) keeps wall-clock time stable across DST;
  a fixed offset (`GMT-5` / `+08:00`) drifts one hour twice a year.

### Solution — Layered Timezone

**Global default timezone (deployment-level) + optional per-job override (job-level).**
When a job declares no timezone, fall back to the global default. This is the minimal
implementation that unlocks cross-region scheduling while staying fully backward compatible:

- Single-deployment-multi-timezone → set a timezone per job. ✅
- Region-isolated deployments (China/US split, separate DBs) → leave every job blank,
  all use the deployment default → **behaves identically to today**. ✅
- Existing rows (`time_zone` NULL) → equal the deployment default = current behavior. ✅
  (No breaking change to old data.)

### Timezone Resolution (single decision point)

Add one helper, e.g. `TimeZoneUtil.resolveZone(JobFlow/JobFlowRun)`:

```
job.timeZone (if set)  →  global default timezone  →  (global default = configurable /
                                                        -Duser.timezone, else JVM default)
```

Everything that touches timezone calls this — never `TimeZone.getDefault()` directly.
Guard: only accept **named** zones (`ZoneId.of(...)`), reject fixed offsets in validation so
DST always works.

### Files to Change

- [ ] `docs/sql/schema.sql` — `t_job_flow` + `t_job_flow_run`: add
      `time_zone varchar(64) DEFAULT NULL COMMENT 'schedule timezone; null = deployment default'`
- [ ] `flink-platform-dao/.../entity/JobFlow.java` + `JobFlowRun.java` — add `String timeZone`
- [ ] `flink-platform-common/.../util/TimeZoneUtil.java` (new) — `resolveZone(...)` layered
      fallback + named-zone validation; keep `Constant.GLOBAL_TIME_ZONE` as the last-resort
      default (optionally make the global default a config property instead of pure
      `TimeZone.getDefault()`)
- [ ] `flink-platform-web/.../quartz/IQuartzInfo.java` — add `TimeZone getTimeZone()`
- [ ] `flink-platform-web/.../quartz/JobFlowQuartzInfo.java` — return
      `resolveZone(jobFlow)`
- [ ] `flink-platform-web/.../service/QuartzService.java:149` —
      `cronSchedule(cron).inTimeZone(quartzInfo.getTimeZone())`
- [ ] `flink-platform-web/.../service/QuartzService.java:162`,
      `controller/QuartzController.java:53`, `dto/request/JobFlowRequest.java:150`,
      `command/dependent/DependentCommandBuilder.java:100` — the 4 `new CronExpression(...)`:
      add `cronExpression.setTimeZone(resolveZone(...))` so preview/validation/dependency
      windows match actual firing
- [ ] `flink-platform-web/.../variable/TimeVariableResolver.java` — `cur*` providers use
      `LocalDate.now(zone)` / `LocalDateTime.now(zone)`; `biz*` anchor (`scheduleTime`)
      converted with the same zone. Zone comes from the job being resolved.
- [ ] `JobFlowRunner.java:127-129` — ensure `scheduleTime` (anchor of `biz*` vars) is stored
      consistently with the job's zone
- [ ] Frontend — one optional "timezone" dropdown on the flow edit page (blank = default).
      No per-user timezone UI.

### Deployment (independent of the code change, do either way)

- [ ] Pin `-Duser.timezone` (or `ENV TZ`) per deployment in `Dockerfile` / startup — China
      `Asia/Shanghai`, US `America/New_York`. **All nodes in one cluster must match** (shared
      Quartz store), and must be **named** zones (DST). This becomes the "global default".

### Out of Scope (avoid over-engineering; add later only if needed)

- Per-user / per-tenant **display** timezone and a global frontend timezone switcher — YAGNI
  for now. Only the schedule-level (per-job) timezone is in scope.
- Migrating all storage to UTC — the layered approach is self-consistent without it, since
  everything already routes through the resolved zone.

### Notes

- End goal is genuine cross-timezone support (per-job), not just "one timezone per
  deployment" — the layered design delivers that while keeping region-isolated deployments
  zero-impact.
- Backward compatibility is the hard constraint: `time_zone` NULL must reproduce today's
  behavior exactly.

### Pitfalls to avoid

- [ ] **Trigger timezone and variable-interpolation timezone are two independent code
      paths — both must be passed the zone explicitly.** Fixing `QuartzService` (the trigger)
      is **not** enough: if `TimeVariableResolver` still uses the JVM default, `${time:...}`
      variables resolve in a different timezone than the schedule fires in — same DB, two
      timezones. Both `cur*` and `biz*` must use the same resolved zone.
- [ ] **DST-safe date arithmetic.** `TimeVariableResolver` currently does
      `destTime.plus(parsedDuration)` on a **`LocalDateTime`** (zone-less), which adds a fixed
      duration and drifts by an hour across a DST switch. When zone matters, do the +/-
      arithmetic on a `ZonedDateTime` in the job's zone, not on `LocalDateTime`/epoch-duration.
- [ ] **Multi-node: pass absolute instants between peers, never local-time strings.** We are
      centerless + gRPC (`JobGrpcServer`/`JobGrpcClient`); if a node sends a formatted local
      time string and the peer parses it in its own timezone, the two disagree. Audit that
      cross-node job payloads carry epoch/`Instant`, not formatted local strings.
- [ ] **Named zones only** (`Asia/Shanghai`, `America/New_York`) — reject fixed offsets and
      ambiguous abbreviations (`CST` means both Beijing and US Central). Enforce in the
      `time_zone` field validation.

