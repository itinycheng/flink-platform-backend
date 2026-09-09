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
- [ ] When archiving run rows, denormalize the parent job/flow name + type into the archive
      row (run rows only carry `job_id`/`flow_id`, which dangle after a purge). No separate
      `t_job` / `t_job_flow` archive table — definitions are low-volume, and their deletion
      trail is already covered by the audit log.
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

> The SQL-layer tenant filter is already live (`@TenantId` + `WorkspaceTenantLineHandler`, 10 tables
> scoped, auto-applied to `getById` / `updateById` / `removeById` / custom queries). What remains is
> propagating `workspace_id` into background threads and deciding fail-open vs fail-close. Only the
> unfinished work is tracked below.

### Guardrail (don't redo)

- **Do not re-add a workspace membership check in `LoginInterceptor`** — it was tried and reverted as
  redundant. `PermissionInterceptor` already blocks a forged `X-Workspace-Id` on `@RequirePermission`
  endpoints; the tenant filter only needs to cover resource-level IDOR (a legit member fetching another
  workspace's row by id).

### The core problem to solve for full rollout

`workspace_id` only exists on HTTP threads (`RequestContext` set by `LoginInterceptor`).
Background / internal threads have none, so scoped-table queries there run **across all workspaces**
(fail-open). Today this is *functionally* safe: the background chain
(`FlowRunDispatcher → FlowExecuteThread → JobExecuteThread`) addresses every scoped table by a precise
key (PK, or `(jobId, flowRunId)`), never a "list all rows in my workspace" query, so the missing filter
changes no result. It becomes a problem the moment (a) a background query starts relying on the filter
to narrow results, or (b) we flip to fail-close (below). Fix = let any entry point populate
`RequestContext`, using two explicit scopes: `runAs(workspaceId, ...)` (already present — the primitive
the entry points below will call) and `runWithoutTenant(...)` (still to be added).

### Work inventory (background context propagation)

**Thread propagation — THE key lever, 1 file:**
- [ ] `ThreadUtil` — decorate task submission to capture the submitting thread's `RequestContext`
      and restore it in the worker thread. All pools go through `ThreadUtil.new*`
      (`FlowExecuteThread`, `JobExecuteThread` virtual, gRPC executor, `CommandMonitor`,
      `ReactiveService`, `WorkerHeartbeat`). Note: a plain `ThreadLocal` does **not** cross a pool
      boundary on its own, and `InheritableThreadLocal` doesn't help with pooled/reused threads — so
      this capture/restore decoration is what actually propagates context down the
      `Quartz → FlowExecuteThread → JobExecuteThread` chain.

**Entry points — set context (`runAs`), workspace comes from the domain object:**
- [ ] `FlowExecuteThread.run` — `runAs(jobFlowRun.getWorkspaceId())`. This is the anchor: it cannot be
      inherited from the `@Scheduled` dispatcher thread (which has no context), so it must be set here.
      Once set, `ThreadUtil` propagation carries it down to `JobExecuteThread` automatically —
      `JobExecuteThread` (holds only `flowRunId`, no workspaceId) then needs no change.
- [ ] `JobFlowRunner.execute` (Quartz) — `runAs(jobFlow.getWorkspaceId())`.
- [ ] `JobGrpcServer` (`processJob` / `killJob` / `savepointJob` / `getJobStatus`) — the remote
      handler also has no context; load JobRun by `jobRunId`, then `runAs(workspaceId)`.

**Entry points — explicit global scans (`runWithoutTenant`, to be added):**
- [ ] `FlowRunDispatcher.drainAndExecute` (@Scheduled) — `listExecutableRunsOnHost(...)` is a genuine
      cross-workspace scan (a worker adopts every workspace's runs on this host). MUST be
      `runWithoutTenant`, not `runAs`.
- [ ] Cron `JobsInJobListStatusChecker` / `UnscheduledJobFlowChecker`, `CommandMonitor` — only if they
      touch scoped tables.

**Optional cleanup (net code reduction):**
- [ ] Remove now-redundant `setWorkspaceId(requireWorkspaceId())` and
      `.eq(workspaceId, requireWorkspaceId())` from ~10 controllers (interceptor does it).

**Policy decision — fail-open vs fail-close:**
- [ ] Decide whether "no context on a scoped-table query" stays **fail-open** (current) or becomes
      **fail-close** (throw). Hard prerequisite for fail-close: every background entry point above must
      first declare `runAs` / `runWithoutTenant`, otherwise the whole
      `FlowRunDispatcher → FlowExecuteThread → JobExecuteThread` chain throws on its first scoped query.
      Also audit raw SQL / JOINs (see Known limits) — the interceptor can't rewrite those, so they
      would silently bypass a fail-close guard.

### Known limits (SQL-layer approach)

- Raw / hand-written SQL and complex JOINs may not be rewritten reliably — audit needed.
- Guarantees MP-mapped column, not the physical DB column (schema/migration owns that).
- Unique constraints that must be per-workspace need `workspace_id` in the composite index.

---

## Failover Hardening — Split-Brain Safety for Reassigned Flow Runs

Centerless scheduler; ownership = `t_job_flow_run.host`; failover = `WorkerHeartbeat.reassignOrphans`
rewriting `host` once a worker's heartbeat is older than `HEARTBEAT_TIMEOUT` (5 min). Core risk: a node
deemed unhealthy keeps executing while a peer takes the run over → **double run**. The design below
mirrors what Airflow (job-heartbeat + zombie reaper + `LocalTaskJob` self-kill + `try_number` identity +
"adopt orphaned tasks") and DolphinScheduler (registry heartbeat + `host`-column ownership +
fault-tolerance re-dispatch) do, but stays MySQL-only / no ZooKeeper / no new scheduled loops.

### Already done (shipped, commit `e1b7fb20`)

- `FlowExecuteThread.ownedByAnotherWorker()` in the wait loop → `releaseInFlight` + abandon orchestration.
- `JobExecuteThread.ownedByAnotherWorker()` also stops when `t_job_flow_run.host != HOST_IP`
  (finished/killing is a separate check, `isFlowRunFinishedOrKilling()`).
- **Guarantee: once a flow is reassigned away, the old node stops launching not-yet-started vertices and
  stops submitting not-yet-submitted jobs.** Covers the *DB-reachable-but-unhealthy* case only. Does NOT
  kill already-running local processes; does NOT cover DB-partition zombies.

### Guiding split by job type (decides how much each layer matters)

- **A-class — externalized execution (Flink/Spark on YARN/K8s):** once the app_id is persisted, any node
  just polls; re-run needs only submit-idempotency + reattach-by-app_id. "Problem is small."
- **B-class — local execution (shell / python / mysql-jdbc):** runs inside the executor node's process
  (`Runtime.exec`, tracked in `CommandExecutor.RUNNING_MAP`). A takeover re-runs from scratch; the only way
  to prevent a concurrent double-run is the old owner **killing its own local process** on lease loss.

### Deferred work (ROI order)

- [ ] **P2 · Submit idempotency guard (no schema, no timer).** In `ProcessJobService.processJob` /
      `CommandExecutor.exec`: if `jobRunId` already in `RUNNING_MAP`, or the job_run already has an app_id /
      is already RUNNING → do NOT resubmit, attach/return instead. `RUNNING_MAP.put` currently overwrites
      without a contains-check, so concurrent same-`jobRunId` submits are not deduped today.
- [ ] **P2 · Persist app_id ASAP.** Today `ProcessJobService.processJob` writes status+app_id only in
      step 5, *after* `exec()` returns. Write the app_id the moment it is known (inside `execCommand`), to
      shrink the "submitted to cluster but app_id not yet in DB" window.
- [ ] **P3 · Executor-side self-kill on lease expiry (B-class).** Reuse the *existing* `CommandMonitor`
      2s loop (`scheduleWithFixedDelay(...,5,2,SECONDS)`): when this node's heartbeat lease is expired,
      iterate `RUNNING_MAP` and `killCommand` each (recursive PID kill via `ShellTask.cancel` /
      `CommandUtil.forceKill` already exists — local op, works under DB partition). Lease = last successful
      `WorkerHeartbeat.reportHeartbeat` write; hold it in a tiny static (the reverted `WorkerLease`,
      ~40 lines, one `AtomicLong`). **No new scheduled task** — renew hooks into the existing 30s heartbeat,
      kill-check into the existing CommandMonitor loop.
- [ ] **Redispatch semantics: `CREATED` instead of `KILLED` on failover.** Failover ≠ failure. On transfer,
      reset the job_run to `CREATED` **and reselect `host`** (via `workerSelectService.randomWorker(routeUrl)`)
      so a live node re-executes it (same row, no retry consumed, no bogus failure). Keep `KILLED` only for
      *user-initiated* stop (`killFlowRun` when flow is KILLABLE/terminal) — add a separate
      `redispatchJob(jobRunId)` path, don't change `killJob`. Order matters: **kill local process first,
      then set CREATED** (else double run). A-class caveat: if an app_id already exists, keep monitoring
      (reattach) — only reset to CREATED when no app_id was obtained. Crash case (not partition): the dead
      node can't self-reset; the reassign/takeover path must reset its leftover job_runs on its behalf.
- [ ] **Phase 1 · Idempotent task identity (optional backstop).** Add `attempt` column to `t_job_run` +
      `UNIQUE(flow_run_id, job_id, attempt)`; `createJobRun` inserts next attempt and reads-existing on
      duplicate-key. Collapses the "two job_run rows created" race to one row. Deprioritized: the sharper
      double-submit ("same row submitted twice") is handled by P2's guard, not this constraint. Retries
      today create a new row per attempt (`getCountAndLastJobRun` counts rows), so the unique key MUST
      include `attempt`; backfill existing rows by id order. = Airflow `try_number`.
- [ ] **Phase 4/5 · Architectural convergence (only if we want to delete the old path).** Add
      `lease_expire_at` to `t_job_flow_run`; make `drainAndExecute` claim via atomic CAS
      `UPDATE ... SET host=me, lease=now+TTL WHERE (host=me OR lease_expire_at<now) AND <non-terminal>`
      (+ `SKIP LOCKED` on the select). Stealing becomes a side effect of normal drain → **delete
      `reassignOrphans` + its `@SchedulerLock`**. Collapses 4 overlapping coordination primitives
      (heartbeat / host / ShedLock / Quartz) into one lease.
- [ ] **Liveness latency knob (no code, config).** MySQL-heartbeat death detection is coarse (5 min) vs
      ZK sub-second. If failover is too slow, shorten heartbeat interval + `HEARTBEAT_TIMEOUT` (e.g.
      15s / 45s) for near-second failover, at the cost of more frequent heartbeat writes. Keep the
      self-fence timeout `< HEARTBEAT_TIMEOUT` with a safety gap.

### Key design decisions (locked in during design discussion)

- **The lease's only irreplaceable role is B-class executor self-kill.** For A-class, submit-idempotency +
  reattach make it redundant. If the platform ever ran only Flink/YARN jobs, the lease could be dropped.
- **Orchestrator host (`t_job_flow_run.host`) ≠ executor host (`t_job_run.host`).** The local subprocess
  lives on the *executor*; self-kill therefore belongs on the executor side (`CommandMonitor`), gated by
  *that* node's heartbeat lease — not the orchestrator's. `reassignOrphans` only rewrites the orchestrator
  host today.
- **Prefer CAS / atomic conditional UPDATE over `SELECT ... FOR UPDATE`.** FOR UPDATE only fits the short
  claim critical section, can't own a run for its (minutes–hours) lifetime, and gives an uncontrollable
  fencing window on connection drop. It does not solve the lease/liveness layer where failover actually
  lives, and is heavier (explicit tx, MySQL 8, connection discipline) for equivalent claim power.
- **True exactly-once needs resource-layer fencing** (deterministic Flink jobName / dedup key so the
  cluster rejects duplicates). DB-only can guarantee "orchestration not duplicated / job_run not
  re-created", not the one submit a partitioned node makes without touching the DB.

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

---

## User-Operation Audit — Method-Annotation Aspect (`@Auditable`)

Record an **operation trail of user actions** (who created / updated / deleted which
`JobInfo` / `JobFlow`), not a row-level data-change log. System-triggered writes
(heartbeat, scheduler, gRPC, background jobs) must **not** be audited. Persist a full JSON
snapshot per operation into `t_audit_log`.

### Chosen approach — Spring AOP aspect on **Controller** methods (not a MyBatis interceptor)

Decided against the SQL-layer `AuditInterceptor` (interceptor sees only SQL, cannot tell a
user action from a system write, and can't express business intent). An aspect on the
user-facing HTTP entry points is the right altitude: system paths never hit a Controller,
so filtering is "annotate-to-audit" by construction.

- [ ] Restore method-level `@Auditable(type, operation)` annotation (`@Target(METHOD)`),
      carrying `EntityType` + `OperationType` — **both declared explicitly** on each method.
- [ ] Reinstate `AuditAspect` (`@Around("@annotation(auditable)")`); delete the abandoned
      SQL-layer `AuditInterceptor` and the class-level `@Auditable` on `JobFlow` / `JobInfo`.
- [ ] Annotate the **user-entry Controller methods** only. Do **not** annotate scheduler /
      gRPC / internal service paths. Coverage:
  - [x] `JobInfoController` — create / update / delete / purge. **DONE** (`EntityType.JOB`).
  - [x] `JobFlowController` — create / update / updateFlow / purge. **DONE** (`EntityType.FLOW`).
        purge is a cascade (1 FLOW + N JOB) but only **one FLOW DELETE row** is written for now —
        child JobInfo rows are not audited (deferred; see cascade note below). `stop` (`STOP`) is
        deferred to the run/schedule batch.
  - [x] `JobFlowRunController.kill(flowRunId)` — `KILL`, `FLOW_RUN`. **DONE**
  - [x] `JobRunController.kill(runId)` — `KILL`, `JOB_RUN`. **DONE**
  - [x] `JobFlowController` — `schedule/start` (`SCHEDULE`), `schedule/stop` (`UNSCHEDULE`),
        `schedule/runOnce` (`RUN`). **DONE**
- [x] Extend enums — **DONE**: `EntityType {JOB, FLOW, FLOW_RUN, JOB_RUN}`,
      `OperationType {INSERT, UPDATE, DELETE, SCHEDULE, UNSCHEDULE, RUN, KILL}`; Javadoc updated.

### Locked-in design decisions (from the design discussion)

- [ ] **Record successful operations only.** Snapshot *after* `proceed()` returns
      successfully. If the business call throws / the tx rolls back, write nothing — "an
      audit row exists" ⇒ "the operation really took effect".
- [ ] **Audit runs outside the business transaction (no shared-fate).** The aspect is the
      **outer** advice (high precedence / low `@Order`); the business `@Transactional`
      commits inside `proceed()`, then the audit `save` runs as an independent write. → No
      need to touch `@EnableTransactionManagement` order, and no need to add
      `@Transactional` to methods like `updateFlowById`.
- [ ] **Audit failures never break the business call.** Wrap the whole audit block in
      try-catch; on failure `log.warn` only, never rethrow.
- [ ] **Snapshot = re-read the row by id via the entity's own service `getById`** (one
      indexed PK lookup; negligible for low-QPS manual ops). Uniform for all ops — avoids the
      "partial updateById returns a half-filled entity" and "DB-generated fields missing"
      problems of snapshotting the return value / input arg directly.
  - [ ] **Do NOT use MyBatis-Plus static `Db.getById`.** Re-read through a `EntityType →
        service::getById` map in the aspect (e.g. `JOB → jobInfoService::getById`). Reasons:
        (1) service methods carry `@DS("master_platform")` — the dynamic-datasource routing
        is activated by the `@DS` AOP on the service call, and static `Db.*` bypasses it
        (a real hazard if platform metadata is ever split across datasources); (2) type-safe
        (`Function<Long, ? extends Identifiable>`) vs `Db`'s raw return + hand-passed
        `.class`; (3) the codebase has zero `Db.*` usage — `service.getById` is the
        established re-read style. Note: audit only re-reads platform metadata tables, so the
        multi-source used for user SQL-job targets is irrelevant here.
  - [ ] INSERT / UPDATE → re-read **after** `proceed()` (captures generated id + untouched
        columns + merged new state).
  - [ ] DELETE → re-read **before** `proceed()` (row is gone afterwards); write the audit
        row after `proceed()` succeeds.
  - [ ] STOP / KILL → same as DELETE: re-read the run **before** `proceed()` to capture the
        live pre-stop/pre-kill state (e.g. `RUNNING`), since the point is "what was killed",
        not the resulting `KILLED` status; write the audit row after `proceed()` succeeds.
        Because the id is a path variable (`/stop/{flowId}`, `/kill/{runId}`) it lands on the
        entityId precedence chain — **no hand-written `AuditLogService.save` needed**, unlike
        the earlier design that assumed the `Long`/`Boolean` return value forced hand-writing.
- [ ] **entityId located by a fixed precedence chain** in the aspect (no SpEL, no per-method
      config): (1) return value — unwrap `ResultInfo`, take `Identifiable.getId()` (covers
      create); (2) a `Long` path variable; (3) the id of the request-body entity. Chosen over
      SpEL: keeps the cross-cutting locate-logic in one place, compile-time-safe,
      consistent, and immune to a future REST-ification that moves `update`'s id from body to
      path (aspect code unchanged; stale body branch stays for back-compat).
  - [ ] If no id can be resolved: `log.warn` (method + sources tried) and **skip** that audit
        row — business call still returns normally.

### Cascade deletes — hand-written audit inside the service (not the aspect)

`JobFlowController.purge` → `JobFlowService.deleteAllById(flowId)` deletes **1 `JobFlow` +
N `JobInfo`** (both audited) in one user action. The aspect sits at the Controller (one
method = at most one audit row) and cannot see the cascaded child ids — they only exist
inside `deleteAllById`. "Splitting the batch delete into per-row SQL" does **not** help:
the Controller-level aspect still fires once, and pushing the aspect down to the
service/mapper layer would reintroduce self-invocation, transaction, and
user-vs-system-filtering problems, plus N× DB round-trips.

**Current state (shipped):** purge writes only **one `FLOW` DELETE** row via the aspect
(pre-read of the flow row at the Controller). The cascaded child `JobInfo` rows are **not**
audited yet. The `AuditLogWriter` below is **deferred** — to be implemented later.

- [ ] Extract a shared `AuditLogWriter` used by **both** the aspect and hand-written call
      sites (single insert path). Design it to be **future-proof for any "delete/modify many"
      case**, not just JobFlow purge:
  - [ ] Keep the API entity-type-agnostic — depend only on `Identifiable` + JSON snapshot, so
        any future batch op (purge-workspace, bulk offline, bulk resource delete, ...) reuses
        it unchanged: `record(EntityType, OperationType, Identifiable)` **plus** a
        `record(EntityType, OperationType, Collection<? extends Identifiable>)` overload.
  - [ ] `operatorId` resolved centrally from `RequestContext.getUserId()` (same as the aspect).
  - [ ] **Transaction isolation is the real design point:** `deleteAllById` is
        `@Transactional`. The writer must keep the aspect's invariants — audit never breaks the
        business call, and audit is written only after the business op succeeds — so the write
        must be isolated (prefer a domain event + `@TransactionalEventListener(AFTER_COMMIT)`,
        or `REQUIRES_NEW`), not an inline same-transaction insert.
- [ ] Then in the `deleteAllById` path, capture the pre-delete snapshots (`jobInfoList` + the
      `JobFlow`) and write **N + 1** rows (N × `JOB` DELETE + 1 × `FLOW` DELETE) through the
      writer. Batch delete stays batched (no perf regression); only the audit inserts loop.

### Guardrail

- [ ] **Any `@Auditable` Controller method must resolve an `operatorId`** from
      `RequestContext.getUserId()`; system paths (no logged-in user) are simply not
      annotated. Document: audited methods live behind the authenticated HTTP layer.

### Deferred

- [ ] **Before + after snapshots per operation.** A row currently holds one snapshot (post-op,
      or pre-op for the `AuditAspect.snapshotsBeforeCall` set). Capture both sides so "who
      changed the cron from X to Y" is answerable without pairing adjacent rows.
- [ ] **`workspace_id` on `t_audit_log`** + `@TenantId` on `AuditLog`. The table is global
      today: `WORKSPACE_VIEW` on any one workspace reads every workspace's snapshots, and
      snapshots contain job configs / SQL.

### Notes

- A `batchId` to group the N+1 rows of a single `purge` into "one operation" is **not**
      added now — same `operatorId` + near-identical `operateTime` already reconstructs the
      scene. Add later only if needed.
- **stop/kill background:** manual **run** already records its executor
      (`JobFlowRunner.execute` stamps `USER_ID` from the Quartz data map onto
      `t_job_flow_run.userId`, falling back to the flow creator for scheduled fires /
      sub-flows). Only **stop / kill** currently record nobody — that gap is what the
      annotations on the three run endpoints close. No schema migration on `t_job_run` /
      `t_job_flow_run`; only the enum extensions above + `t_audit_log` rows.
- Rejected for stop/kill: a plain `log.info("... killed by {}", userId)` — zero schema cost
      but not queryable and lost on log rotation. Fine only for pure debugging.
- (Optional) surface stop/kill entries in the existing audit-log UI / `AuditLogController`.

---

## Global `/api` Path Prefix (BREAKING — deferred)

All 23 controllers currently sit on bare root paths (`/jobInfo`, `/worker`, `/stats`, ...),
sharing one path namespace with the bundled SPA (frontend build output is copied into
`flink-platform-web/src/main/resources/static`, served by the same app on port 9104).

**Why it's worth doing** (all true today, independent of any REST-ification):

- **Reverse proxy can't split traffic.** Routing static→CDN / API→backend needs
  `location /api/`; today nginx must enumerate every controller path and be edited whenever
  a controller is added.
- **SPA client-side routes collide** with API paths (a frontend route named `/jobFlowRun` is
  a natural choice and would clash).
- **Filters can't be path-scoped** — auth / CORS / rate-limiting have no prefix to hang off.

### Implementation

`AppConfiguration` already implements `WebMvcConfigurer`; all 23 controllers are
`@RestController` (verified — zero bare `@Controller`), so one method covers everything and
leaves static resources untouched:

```java
@Override
public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.addPathPrefix("/api", HandlerTypePredicate.forAnnotation(RestController.class));
}
```

- [ ] Add `configurePathMatch` to `AppConfiguration`
- [ ] **Prefix `protectedPaths` in the same commit** (see trap below)
- [ ] Frontend: axios `baseURL` (one place)

**Do NOT use `server.servlet.context-path: /api`** — it moves the whole app including the
SPA (`/api/index.html`), which defeats the purpose of separating API from static assets.

### ⚠️ Trap: adding the prefix silently disables ALL authentication

`AppConfiguration.addInterceptors` uses a **hardcoded path whitelist**:

```java
String[] protectedPaths = { "/jobInfo/**", "/jobRun/**", "/jobFlow/**", "/jobFlowRun/**", ... };
registry.addInterceptor(loginInterceptor).addPathPatterns(protectedPaths);
registry.addInterceptor(permissionInterceptor).addPathPatterns(protectedPaths);
```

After prefixing, requests arrive as `/api/jobInfo/**` and **none of these patterns match** →
both interceptors stop running. There is **no spring-security dependency and no servlet
`Filter`** in the project (verified), so these two interceptors are the *only* auth layer:
the entire API becomes anonymously accessible, with no error and no failing test unless one
specifically asserts unauthenticated access is rejected.

`protectedPaths` must be prefixed in the same commit. Better: extract an `API_PREFIX`
constant and build the array from it so the two can't drift again.

### External contracts that break (need manual coordination, not code)

- [ ] **Grafana webhook** — `GrafanaWebHookController` is `/webhook`; the URL configured on
      the Grafana side must become `/api/webhook` or alert callbacks fail silently.
- [ ] **Login / SSO** — `LoginController` has a bare `@RequestMapping` (no base path), so its
      endpoints live at the root and become `/api/login` etc. Any redirect URI registered
      with an external IdP must be updated.

### Related deferred items (separate from this one, don't lose them)

- [ ] **GET used for mutations** — `GET /jobFlowRun/kill/{id}`, `/jobInfo/delete/{id}`,
      `/jobInfo/purge/{id}`, `/worker/delete/{workerId}` etc. violate GET's safe/idempotent
      contract. Real consequences: trivial CSRF (`<img src=".../kill/123">` needs no JS or
      form), link prefetch by browsers/chat clients, proxy/CDN caching, crawlers. Should
      become POST/DELETE. **Higher priority than the prefix — this is a security bug, not
      style.**
- [ ] **No OpenAPI spec** — no `springdoc`/`swagger` dependency exists. For an open-source
      release, generated API docs + clients matter more than URL aesthetics.
- [x] **Unprotected controllers** — **DONE** for `/stats`, `/audit-logs`, `/quartz`, `/flink`
      (added to `protectedPaths`). `/webhook` stays open (intentional); `/attr` serves static
      enum metadata and carries no `@RequirePermission`.
- [ ] **`/reactive/**` is still unprotected — blocked on node-to-node auth.** `POST
      /reactive/execJob` executes ad-hoc SQL / Flink SQL and is reachable without login; its
      `@RequirePermission(TASK_EXEC)` / `TASK_VIEW` are inert. Cannot simply be added to
      `protectedPaths`: `ReactiveController` forwards to a peer via `RestTemplate`
      (`routeUrl + "/reactive/execJob"` / `execLog`) with **no auth header**, so protecting the
      path 401s every cross-node forward. Prerequisite: propagate a credential on the forward,
      or move these two endpoints to gRPC (the class's own TODO). Do both in one commit.
- [ ] **API versioning (`/api/v1`)** — decided as *optional*. A `/v1` segment provides no
      compatibility by itself (discipline does: add fields, never remove/rename/re-semantic),
      and most projects never ship a v2. Its only real argument is cost asymmetry: three
      characters now vs a breaking change later. Decide when adding the prefix; not required.

