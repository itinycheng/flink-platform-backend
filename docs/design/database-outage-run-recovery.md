# Database outage and run recovery

> **Status:** Split into two proposals for owner review, 2026-11-20. No runtime changes
> implemented.

- [Short platform database outages](short-database-outage-recovery.md) is the current priority:
  pause unsafe FlowRun/JobRun decisions, report database failure, and continue the same
  persisted runs after connectivity and ownership checks.
- [Major incident and manual schedule recovery](major-incident-recovery.md) is deferred:
  a durable cluster switch, cross-date review, Quartz misfires, and selected backfill.

These problems have different recovery policies. The short-outage behavior does not require
the manual recovery switch or a batch backfill feature.
