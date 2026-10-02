# TODO

## Schedule-Time-Aware Run Concurrency

Phase 1 is described in
[docs/design/schedule-time-concurrency.md](docs/design/schedule-time-concurrency.md): different
logical schedule times may overlap up to `ExecutionConfig.maxActiveRuns`; the same logical time is
still treated as a duplicate. `maxJobsPerRun` remains the number of jobs within one flow run, with
the legacy JSON/API name `parallelism` retained for compatibility.

- [x] Separate same-schedule duplicate detection from active-run capacity.
- [x] Keep the default at one active flow run for backward compatibility.
- [x] Apply the logical-time check to cron, manual flow, and subflow entry points.
- [ ] Extend the model to flow-less/ad-hoc job runs after they have a persisted logical-time field.
- [ ] Make admission atomic across scheduler nodes (database claim/lock).
- [ ] Add queue/discard overlap policies and terminal-run reprocessing policy.
- [ ] Add a date-range backfill API with its own concurrency limit and progress tracking.

---

## Execution Log Archival (`t_job_run` / `t_job_flow_run`)

Keep the hot tables small by moving aged rows into monthly-partitioned archive tables, so
operational queries stay fast as execution history grows over years. MySQL-only, no new
storage dependency.

See design: [docs/design/execution-log-archival.md](docs/design/execution-log-archival.md)

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
- [ ] `flink-platform-web/.../quartz/QuartzSpec.java` — add `TimeZone getTimeZone()`
- [ ] `flink-platform-web/.../quartz/JobFlowQuartzSpec.java` — snapshot
      `resolveZone(jobFlow)`
- [ ] `flink-platform-web/.../quartz/QuartzService.java` — configure the trigger with
      `cronSchedule(cron).inTimeZone(quartzInfo.getTimeZone())`
- [ ] `flink-platform-web/.../quartz/QuartzUtil.java` — make cron calculation and validation
      accept the resolved zone and call `cronExpression.setTimeZone(...)`; update the preview and
      dependency callers so their windows match actual firing
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

## User-Operation Audit — Remaining Work

- [ ] **Audit cascade deletes** — extract a shared `AuditLogWriter`, then have
      `JobFlowService.deleteAllById` capture the deleted flow and child jobs and write N + 1
      audit rows after the business transaction commits. Audit failure must not fail the
      business operation.
- [ ] **Require an operator for audited entry points** — every `@Auditable` controller method
      must resolve `RequestContext.getUserId()`; system paths remain unannotated.
- [ ] **Store before and after snapshots** so changes can be understood without correlating
      adjacent audit rows.
- [ ] **Tenant-scope audit logs** — add `workspace_id` to `t_audit_log` and `@TenantId` to
      `AuditLog`; the table is global today and can expose snapshots across workspaces.
- [ ] (Optional) Surface schedule/run/kill entries in the existing audit-log UI.

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


---

## Alerting Strategy — Remaining Work

See design: [docs/design/alerting-strategy.md](docs/design/alerting-strategy.md)

- [ ] **Structured dependency diagnostics** — distinguish no upstream run, still running,
      expired result, and settled-but-unmatched status. `DependencyService#evaluate` currently
      returns only one group verdict, so timeout alerts cannot state the exact cause.
- [ ] **Throttle the direct channel** — `sendAlertsDirectly` de-duplicates nothing, so
      `JobFlowRunner`'s duplicate-run guard fires once per cron tick while an upstream stalls.
      De-dup by `(flowId, alert kind)` inside `dispatch`, never throttling terminal-status
      notifications. Requires modelling an "alert kind", which is the bulk of the work.
- [ ] **Stop forging `FAILURE` in `sendErrAlertsDirectly`** — the run is synthetic and nothing
      failed; the trigger was *skipped*. Recipients routing by status see a false failure, and
      direct mode bypasses `AlertConfig.statuses` too. Schedule separately: it changes existing
      alert payloads.
- [ ] **A settled-unmatched group is alerted as a timeout** — once `evaluate` reaches its last
      line nothing is pending any more: every upstream has given its final answer and none is
      accepted. Under the default `onUnmatched=WAIT` it still returns `PENDING`, so the run
      waits out its deadline and then alerts "Waiting timeout, dependencies not satisfied",
      blaming a slow upstream for what is a settled failure. `DependencyService` knows the
      difference — that is what `UNSATISFIABLE` vs `PENDING` is for — but drops it at the group
      level. Fold into the first item above: both need `evaluate` to surface more than one
      verdict.
- [ ] **A downstream scheduled denser than its upstream alerts every cycle** — the deadline is
      the downstream's own next cron fire, so an hourly flow depending on a daily one fails and
      alerts ~23 times a day, every day. Nothing warns that the dependency's expiry window is
      longer than the flow's own schedule interval. Cheapest fix is a save-time validation in
      `JobFlowRequest#verifyDependsOn`; throttling would only mask it.

### Long-term: user-defined, event-driven alert policies

The fixed WAITING warning is a compatibility-sized fix, not the final alert model. Do not
keep adding timing branches to `FlowRunDispatcher` or boolean variants to `AlertSendingService`.
The target is for a flow owner to configure alert rules by event, independently from the runtime
rule that decides when a run changes status.

- [ ] **Define stable alert event types** — initially `WAITING_TOO_LONG`, `WAITING_TIMEOUT`,
      `EXECUTION_TIMEOUT`, `RUN_STATUS_CHANGED`, and `TRIGGER_SKIPPED`. Event producers report
      facts; they do not decide recipients, repetition, or escalation.
- [ ] **Keep alert payloads structured until channel rendering** — event producers provide typed
      fields plus `List` / `Map` data (for example dependency details), rather than pre-formatting
      one display string. Each channel adapter owns formatting, escaping, field limits and line
      breaks for its target representation: HTML/email, SMS/plain text, Feishu cards, and future
      channels. Do not inject raw rendered text into an already-serialized JSON template.
- [ ] **Add per-flow alert rules** — each rule selects an event and alert channel(s), with optional
      event-specific conditions such as `after`, terminal statuses, severity, and repeat/escalation
      policy. Preserve existing `AlertConfig.statuses` as the backwards-compatible implicit
      `RUN_STATUS_CHANGED` rule.
- [ ] **Separate lifecycle deadlines from notification timing** — for example, dependency wait
      timeout determines when `WAITING -> FAILURE`, while a `WAITING_TOO_LONG.after` rule only
      determines when the user is notified. Changing an alert rule must not change scheduling.
- [ ] **Resolve policy by scope** — platform defaults, then workspace defaults, then flow
      overrides. Administrators provide safe defaults and limits; flow owners choose the useful
      signals and destinations within those limits.
- [ ] **Centralise noise and delivery governance** — idempotency by event/rule/run, throttling,
      silence and maintenance windows, escalation, delivery retry, and an auditable delivery
      history. Terminal run notifications must not be lost to throttling. Persistent idempotency
      should eventually replace the minimal WAITING warning's in-memory set.

The next migration step is to introduce an internal `AlertEventType` when per-flow rules begin,
then add central policy evaluation and persistent delivery state. Existing status-based alert
configuration should continue to work throughout the migration.

---

## Replace the WAITING Cron-Deadline Workaround

- [ ] `calcWaitDeadline` currently expires a WAITING run ten seconds before the next cron fire
      to reduce collisions with `JobFlowRunner`'s non-terminal-run guard. If this heuristic proves
      unreliable, replace it with explicit supersession: atomically fail the previous WAITING run
      before creating the next scheduled run. Check the `JOB_LIST` branch at the same time.

---

## Flow Dependencies (`depends_on`) — Remaining Work

Backend is in place (`DependencyService`, `FlowRunDispatcher#advanceWaitingRuns`, `WAITING`
status, `t_job_flow(_run).depends_on`). What is left, roughly in the order it blocks use.

See design: [docs/design/flow-dependency-model.md](docs/design/flow-dependency-model.md)

### Validation gaps (`JobFlowRequest#verifyDependsOn`)

- [ ] **Detect dependency cycles across flows** — only self-dependency is rejected today
      (`flowId.equals(getId())`). A → B → A saves fine, then both flows sit in `WAITING` every
      cycle until their deadlines and fail, with nothing in the alert pointing at the cycle.
      Needs a walk over other flows' `depends_on` at save time.
- [ ] **Bound the serialised size** — the column is `varchar(2048)` and nothing checks the
      JSON length. Past the limit MySQL either rejects the write (strict mode) or truncates it
      into unparsable JSON that blows up on the next read.
- [ ] **Refuse to delete a flow other flows depend on** — `JobFlowService#deleteAllById` drops
      the flow (and its runs) unconditionally. Downstreams then wait out every deadline with no
      hint that the upstream is gone: no row comes back, so it looks the same as "never ran".

### Cleanups

- [ ] **Rename `ExecutionCondition` → `TriggerRule`** — its own class javadoc carries the TODO.
      Either do it or drop the note; it is a DAG-wide rename plus the `@JsonAlias` compatibility
      surface.
### Frontend (low priority — new UI only)

The old UI will not get this feature; it only has to keep working, which it does: it never
posts `dependsOn`, so `hasDependencies()` is false and runs go straight to `SUBMITTED`, and no
run of its can reach `WAITING`.

- [ ] **New UI: flow-level dependency editor** — `flowId` / `statuses` / `expiry` / `duration`
      per item, plus `precondition` and `onUnmatched` per group. `ExecutionCondition` accepts
      the legacy `AND` / `OR` via `@JsonAlias`, so the existing dependent-job editor is a
      starting point.
- [ ] **New UI: `WAITING` is a new `ExecutionStatus` (code 11)** — needs a label, a colour and
      a filter entry wherever run statuses are rendered.
