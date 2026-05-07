# Execution Log Archival

> **Status:** Design — not yet implemented. Tracked in [TODO.md](../TODO.md).

## Problem

`t_job_run` and `t_job_flow_run` accumulate one row per job / flow execution.
On a cluster with moderate workload (thousands of flows × hourly schedules) the tables grow by
tens of millions of rows per year. Over several years they reach a size where:

- Dashboard queries that aggregate historical success rates become slow.
- Deep pagination / `ORDER BY create_time DESC` slows down noticeably.
- Ad-hoc `DELETE` for cleanup produces large binlogs and lock pressure.

The goal is to keep the platform responsive as execution history grows, **without introducing
a new storage dependency** (the project must remain usable with MySQL alone).

## Scope

In scope:

- `t_job_run` — per-job execution record
- `t_job_flow_run` — per-flow-run record

Out of scope for now: `t_job`, `t_job_flow` (configuration tables, bounded size).

## Rejected Alternatives

| Alternative | Why not |
|---|---|
| **RANGE partition on the live table** | Operational queries (`WHERE id = ?`, `WHERE flow_run_id = ?`) do **not** filter by time. Secondary indexes are local to each partition, so these queries probe every partition's index — the common path gets slower, not faster. |
| **Shard by year (`t_job_run_2026`, ...)** | Requires application-side routing; schema evolution (`ALTER TABLE`) must be replayed on every historical table or left as drift. Cross-year queries need manual `UNION ALL`. High code complexity for an unclear win. |
| **Hash shard (`t_job_run_0..15`)** | Addresses write hotspots we do not have. Makes time-based cleanup harder, not easier. |
| **External cold store (ClickHouse, ES, S3)** | Correct for very large deployments, but a hard dependency is too heavy for an open-source scheduler. Should remain a pluggable extension point, not the default. |
| **`DELETE` on a schedule, no archive** | Viable as the simplest mode, but loses history that some users need for audit / analytics. Should be one mode, not the only mode. |

## Chosen Design

**Hot table kept small. Archive table holds everything else.**

```
┌──────────────────────┐   archive job (daily)     ┌──────────────────────────┐
│ t_job_run (hot)      │  moves rows older than    │ t_job_run_archive        │
│ unpartitioned        │  hot-retention-days       │ RANGE-partitioned by     │
│ ~ 1M rows            │ ────────────────────────► │ month(create_time)        │
│ all operational SQL  │                           │ dashboard / audit SQL    │
│ untouched            │                           │ DROP PARTITION to evict  │
└──────────────────────┘                           └──────────────────────────┘
```

Two independent retention knobs:

- `hot-retention-days`: how long a row stays in the hot table.
- `archive-retention-months`: how long a row stays in the archive before a partition is dropped
  (`0` = keep forever).

### Why this works

- Hot table stays small (~3 months of rows). Every operational query (`getById`, `findRunningJob`,
  `lastJobRunList`) runs against a compact unpartitioned table — same semantics as today, faster
  than today.
- Archive queries are **always** time-ranged (dashboard "last N months", audit by date window), so
  partition pruning on `create_time` is almost always effective. Non-time lookups against the
  archive are rare and slow-tolerant.
- Monthly partitioning (not yearly) allows fine-grained retention ("keep 18 months") and produces
  small, manageable partitions.
- No new storage system. Same MySQL, same connection pool, same backup strategy.

### Why monthly, not yearly

For the archive table specifically, the usual downside of many partitions — "`WHERE id = ?` has to
probe every partition" — does not bite, because archive traffic is time-ranged.
Monthly partitioning gives:

- Precise retention windows (months, not years).
- Small partition sizes, so `DROP PARTITION` stays a cheap metadata operation and any future
  DDL on the archive rewrites one month at a time if needed.
- "Recent 30 days" dashboard queries scan one partition, not a whole year.

## Schema Changes

### Hot tables: no change

`t_job_run` and `t_job_flow_run` stay exactly as defined in `docs/sql/schema.sql`. No primary-key
change, no partitioning. Existing application code and queries are unaffected.

### Archive tables

Two new tables, same columns as the hot tables, with monthly RANGE partitioning. The partition
key must be part of the primary key, so the PK becomes `(id, create_time)`.

```sql
CREATE TABLE `t_job_run_archive` LIKE `t_job_run`;

ALTER TABLE `t_job_run_archive`
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (`id`, `create_time`);

ALTER TABLE `t_job_run_archive`
    PARTITION BY RANGE (TO_DAYS(`create_time`)) (
        PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
        PARTITION p202602 VALUES LESS THAN (TO_DAYS('2026-03-01')),
        PARTITION p202603 VALUES LESS THAN (TO_DAYS('2026-04-01')),
        -- ... provisioned ahead of time by the maintenance job
        PARTITION pmax    VALUES LESS THAN MAXVALUE
    );

-- Same treatment for t_job_flow_run_archive.
```

Notes:

- `TO_DAYS(create_time)` is chosen over `YEAR()` / `MONTH()` because it supports clean monthly
  boundaries including leap years.
- `pmax` is a catch-all. The maintenance job reorganizes it before new months arrive — data must
  never land in `pmax` under normal operation.
- Indexes on the archive should match the hot table only where dashboard queries actually need
  them. Keeping the archive lean on indexes reduces write cost during archival batches.

## Archive Job

A single scheduled task per table: move rows older than the retention window into the archive,
then drop them from the hot table. Implemented as a Spring-managed bean and scheduled via the
existing Quartz infrastructure (can also be exposed as a built-in `JobFlow` — eating our own dog
food).

### Algorithm

```
loop:
    rows = SELECT * FROM t_job_run
           WHERE create_time < NOW() - hot-retention-days
           ORDER BY id
           LIMIT batch-size

    if rows empty: break

    BEGIN TRANSACTION
        INSERT INTO t_job_run_archive (...) VALUES (...)   -- batch insert
        DELETE FROM t_job_run WHERE id IN (...)
    COMMIT

    sleep(inter-batch-ms)    -- throttle, relieve replica lag
```

Properties:

- **Batched**: `batch-size` defaults to 10 000 rows. Keeps transactions small, binlog manageable,
  replica lag bounded.
- **Idempotent on restart**: if the job crashes mid-loop, the next run simply picks up where it
  left off — rows that were already archived are gone from the hot table.
- **No gaps**: the archive `INSERT` and the hot `DELETE` are in the same transaction. A row
  can never be lost; at worst it is temporarily duplicated if the commit is observed mid-flight
  by a reader on a non-repeatable-read isolation — acceptable since the archive is for
  read-only historical consumption.

### Eviction of the archive

Separate step, runs at the end of the archive job (or on its own cron):

```sql
ALTER TABLE t_job_run_archive DROP PARTITION p202301;
```

Determined from `archive-retention-months`. `0` or negative → no eviction.

## Partition Maintenance Job

Partitions must exist **before** data wants to land in them, otherwise inserts fail. A separate
scheduled task runs monthly (default: 25th of each month) and does:

```sql
ALTER TABLE t_job_run_archive REORGANIZE PARTITION pmax INTO (
    PARTITION p202607 VALUES LESS THAN (TO_DAYS('2026-08-01')),
    PARTITION pmax    VALUES LESS THAN MAXVALUE
);
```

Provisioning 1–2 months ahead is sufficient. The job is idempotent — if the partition already
exists, it is a no-op.

## Configuration

All behavior is driven by configuration; nothing is hard-coded. Apollo-backed overrides are
respected, so operators can tune retention without a restart.

```yaml
flink-platform:
  archive:
    # Master switch. false → skip archiving entirely (compatible with mode below).
    enabled: true

    # Mode: "archive" moves rows into the archive table, "delete" just removes them.
    # "delete" is the lightest option for small/embedded deployments.
    mode: archive        # archive | delete

    # Rows older than this are removed from the hot table.
    hot-retention-days: 90

    # Archive partitions older than this are dropped. 0 = keep forever.
    archive-retention-months: 36

    # Quartz cron for the archive job (default 03:00 daily).
    archive-cron: "0 0 3 * * ?"

    # Quartz cron for the partition maintenance job (default 02:00 on the 25th).
    partition-maintenance-cron: "0 0 2 25 * ?"

    # Rows moved per transaction. Tune to replica lag budget.
    batch-size: 10000

    # Sleep between batches in milliseconds. Throttle knob.
    inter-batch-ms: 200

    # Per-table overrides. Unset fields inherit the defaults above.
    tables:
      t_job_run:
        hot-retention-days: 90
        archive-retention-months: 36
      t_job_flow_run:
        hot-retention-days: 180
        archive-retention-months: 60
```

## Dashboard Query Changes

Analytical endpoints that currently query the hot table for time-range aggregates need to be
redirected to the archive table (or a `UNION ALL` of both, depending on the retention window
in the query). Candidates:

- `countJobRunGroupByStatus` — filters by `stop_time BETWEEN ...`
- `countJobFlowRunGroupByStatus` — filters by `end_time BETWEEN ...`
- Any controller endpoint that accepts a date range spanning beyond `hot-retention-days`.

Operational endpoints (`findRunningJob`, `lastJobRunList`, `getLiteById`, etc.) continue to
read from the hot table only. They are the hot path and must stay fast.

## Migration Strategy

For existing deployments with large legacy `t_job_run` / `t_job_flow_run` tables:

1. Create the archive tables (DDL above). Cheap — empty tables.
2. Backfill: run the archive job with an aggressive `batch-size` and `inter-batch-ms` during a
   low-traffic window. Can be interrupted and resumed.
3. Once the hot table is back to `hot-retention-days` worth of rows, enable the regular
   (daily) schedule.
4. Dashboard endpoints are updated to query the archive in a separate release.

For new deployments: tables start empty. The archive table and jobs are created at bootstrap;
archival kicks in once rows exceed `hot-retention-days`.

No `pt-online-schema-change` is required for the hot tables since their schema is unchanged.

## Extension Point (Future)

Operators with an external data warehouse (ClickHouse, S3, etc.) should be able to plug in
their own archive target. Proposed SPI, matching the existing `CommandBuilder` / `CommandExecutor`
pattern:

```java
public interface JobRunArchiver {
    boolean isSupported(String type);            // "mysql-table" | "clickhouse" | ...
    int archive(List<JobRunInfo> rows);          // returns rows archived
    void evict(LocalDateTime olderThan);         // drop/delete old data in the target
}
```

Default implementation: `MysqlTableArchiver` (this document). External implementations live
outside the core module and are discovered via Spring bean scanning.

This SPI is **not** part of the initial delivery. The first version ships with the MySQL
archive only, and the interface is introduced when a second implementation is actually needed.

## Open Questions

- Should the archive job be modeled as a built-in `JobFlow` (visible in the UI, observable
  via the standard job-run history) or a plain `@Scheduled` bean? Modeling it as a flow is
  elegant but creates a bootstrap concern — the archival flow itself writes to `t_job_run`.
- How to handle in-flight jobs at the retention boundary? Current plan: only rows with a
  terminal `status` are eligible for archival, `create_time` is used as the selector but
  `status IN (terminal set)` is an additional filter. Needs confirmation against the full
  status enum.
- Indexes on the archive — which ones are worth keeping? Heavy dashboard queries will tell us;
  starting point is `create_time` + `status` + `flow_id` / `flow_run_id`.
