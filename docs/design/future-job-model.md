# Future Job Definition Model

> **Status:** Deferred architecture note. This document records terminology and a possible future
> schema direction only. It does not authorize or schedule a migration.

## Context

The current model uses `t_job_flow` for the schedulable definition and `t_job` for an executable
node inside that definition. This naming works while the main aggregate is a job flow, but becomes
misleading once `FLOW`, `FLINK`, `SPARK`, and `SQL` are peer top-level definition types.

The existing structural relationship can remain useful:

- a `FLINK`, `SPARK`, or `SQL` definition has exactly one executable node;
- a `FLOW` definition has one or more executable nodes plus their graph relationships.

The future change is therefore primarily a terminology and ownership cleanup, not necessarily a
new relational shape.

## Preferred domain terminology

Use explicit names in Java and API contracts:

```text
JobDefinition
└── JobNodeDefinition

JobRun
└── JobNodeRun
```

- `JobDefinition` is a user-visible object that can be scheduled or run independently. Its type is
  `FLOW`, `FLINK`, `SPARK`, or `SQL`.
- `JobNodeDefinition` is an executable node owned by a job definition.
- `JobRun` is one logical scheduled or manually triggered execution.
- `JobNodeRun` is the execution of one node within that job run.
- If retry attempts later need their own identity, use `Attempt` rather than overloading `Run` or
  `Instance`.

`Node` is preferred over `Task` because Spark and Flink already give `Task` and `Subtask` specific
engine-level meanings. Using `Node` keeps the platform orchestration layer distinct from engine
internals and also naturally covers condition, dependency, and sub-flow nodes.

## Candidate physical names

The concise candidate is:

```text
t_job
t_job_node
t_job_run
t_job_node_run
```

Here, tables without `_run` contain definitions. If explicit physical names are later considered
more valuable than brevity, the definition tables may instead be named
`t_job_definition` and `t_job_node_definition`. The final DDL naming decision is intentionally
deferred.

The likely conceptual mapping from the current schema is:

| Current table | Future role | Candidate table |
|---|---|---|
| `t_job_flow` | Top-level schedulable definition | `t_job` |
| `t_job` | Executable node definition | `t_job_node` |
| `t_job_flow_run` | Top-level logical execution | `t_job_run` |
| `t_job_run` | Node execution | `t_job_node_run` |

Do not use `job_detail` for the child table. A detail table conventionally represents a 1:1
vertical split, while a `FLOW` owns multiple independently executable nodes.

## Cardinality

```text
JobDefinition(type = FLINK | SPARK | SQL)  1 ── 1 JobNodeDefinition
JobDefinition(type = FLOW)                 1 ── N JobNodeDefinition
```

The application domain must enforce the type-dependent cardinality. A `FLOW` additionally owns a
DAG, stored either as the current serialized graph or in normalized node-edge tables; that choice
is outside this note.

## Deferred decisions

Revisit this model before introducing peer `SPARK` or `SQL` top-level definitions, or before
renaming the existing `JOB_FLOW` type to `FLOW`. At that time, decide:

1. the final physical table names;
2. whether nodes are owned exclusively by one job or may reference another top-level job;
3. whether the DAG remains serialized or becomes normalized;
4. the compatibility and data-migration sequence for existing tables and APIs.

Until that review, the current schema and names remain unchanged.
