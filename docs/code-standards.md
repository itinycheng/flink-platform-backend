# Code standards

[AGENTS.md](../AGENTS.md) defines the development and verification workflow. Apply these
project-specific constraints to the affected code.

## Contracts

- For API or persistence changes, state payloads, validation, defaults, schema migration, and
  rollout order. Coordinate the contract with `runnelo-ui` when its screens are affected.
- Preserve compatibility for identified consumers and describe the migration path for a breaking
  change. Avoid extensions for hypothetical use cases.
- For Quartz, flow state, worker reassignment, or external job submission, check concurrency,
  retries, and recovery. State the ownership and idempotency assumptions.

## DAO naming direction

For new custom mapper methods, use SQL verbs: `select*` (including aggregates), `insert*`,
`update*`, or `delete*`. Service reads should express their return contract: `get*` returns one
non-null object or throws `DefinitionException`; `find*` may return `null`; `list*` and `map*`
return empty values rather than `null`; `page*` returns `IPage`; `count*` and `exists*` return
scalars. Include the key in map method names. For writes, prefer a domain verb such as `submit*`
or `cancel*` when it conveys more than generic CRUD. Do not use mapper-style `select*` or `query*`
names for service methods.

This is a target for new methods and gradual migration of affected callers. Existing names and
inherited MyBatis-Plus methods may have different contracts; inspect them before relying on a
prefix. Keep unrelated naming changes separate.

## Build boundaries

- The root project targets Java 21. `flink-platform-common`, `flink-platform-udf`, and
  `flink-sql-submit-*` target Java 8; avoid newer syntax and APIs in those modules.
- Maven runs Spotless and Checkstyle at `validate`. NullAway checks `@NullMarked` code during
  compilation. Do not disable project-wide checks to make a change compile.
- Keep credentials and machine-specific configuration out of source, tests, documentation, and
  handoff output.
