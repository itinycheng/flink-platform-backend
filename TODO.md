# TODO

## WorkspaceScopedService — Automatic Workspace Isolation for Save/Update/Delete

### Problem

Controllers correctly inject `workspaceId` on `create`, but `getById`, `updateById`, and
`removeById` have no workspace ownership check. Any authenticated user who knows a resource ID
can read, modify, or delete resources belonging to another workspace.

### Solution

Introduce a `WorkspaceScopedService` abstract base class that overrides the standard
MyBatis-Plus methods to enforce workspace ownership transparently. Controllers require no
changes beyond removing now-redundant `setWorkspaceId(...)` calls.

### Architecture

```
dao layer (no dependency on web layer)
  WorkspaceOwned           entity interface — exposes getWorkspaceId() / setWorkspaceId()
  WorkspaceIdProvider      decoupling interface — defined in dao, implemented in web
  WorkspaceScopedService   abstract base — overrides save / getById / updateById / removeById

web layer
  RequestContextWorkspaceIdProvider   reads ThreadLocal, implements WorkspaceIdProvider
```

**Fallback rule:** when `workspaceId == null` (non-HTTP context: Quartz, schedulers, etc.),
all overridden methods delegate directly to the super implementation — no side effects.

### Behavior of Overridden Methods

| Method               | HTTP context (wsId present)                              | Non-HTTP context (wsId == null) |
|----------------------|----------------------------------------------------------|---------------------------------|
| `save(entity)`       | auto-sets `workspaceId` if not already set               | delegates to super              |
| `getById(id)`        | returns `null` if resource belongs to another workspace  | delegates to super              |
| `updateById(entity)` | returns `false` if resource belongs to another workspace | delegates to super              |
| `removeById(id)`     | returns `false` if resource belongs to another workspace | delegates to super              |

### Files to Create

- [ ] `flink-platform-dao/.../entity/WorkspaceOwned.java`
- [ ] `flink-platform-dao/.../service/WorkspaceIdProvider.java`
- [ ] `flink-platform-dao/.../service/WorkspaceScopedService.java`
- [ ] `flink-platform-web/.../config/RequestContextWorkspaceIdProvider.java`

### Entities — add `implements WorkspaceOwned` (class declaration only, Lombok handles the rest)

- [ ] `AlertInfo` / `CatalogInfo` / `Datasource` / `TagInfo` / `Resource` / `JobParam` / `JobFlow`

### Services — change `extends ServiceImpl` → `extends WorkspaceScopedService`

| Service              | Entity        |
|----------------------|---------------|
| `AlertService`       | `AlertInfo`   |
| `CatalogInfoService` | `CatalogInfo` |
| `DatasourceService`  | `Datasource`  |
| `TagInfoService`     | `TagInfo`     |
| `ResourceService`    | `Resource`    |
| `JobParamService`    | `JobParam`    |
| `JobFlowService`     | `JobFlow`     |

### Controller Cleanup

- [ ] Remove explicit `setWorkspaceId(RequestContext.requireWorkspaceId())` calls from all `create`
  methods — `save()` now injects it automatically
- [ ] `CatalogInfoController.delete`: replace `remove(QueryWrapper with workspaceId condition)` with
  `removeById(catalogId)` — ownership is enforced by the base class

### Out of Scope

- `JobFlowRun` — `workspaceId` is set internally from `JobFlow` by the scheduler, not via HTTP
- `JobInfo` — has no `workspaceId` field
- `list`/`page` queries with manual `eq(workspaceId)` conditions — remain unchanged

---

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

