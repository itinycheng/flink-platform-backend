# Guidance for coding agents

This is a distributed workflow scheduler. Quartz and MySQL coordinate flow runs; workers execute DAG jobs through gRPC.

The owner sets the goal and reviews each meaningful implementation increment. Agents investigate, design, implement,
compile, and hand off a focused diff. Routine implementation choices do not need separate approval. Follow the owner's
requested review cadence; if the owner asks for end-to-end implementation, complete it before the handoff.

## Start a task

1. Read this file, [code standards](docs/code-standards.md), and [lessons](LESSONS.md). Read only the architecture or
   design documents relevant to the task.
2. Check `git status` before editing. Preserve existing user changes, including changes in the embedded frontend
   directory. Use a separate worktree when concurrent sessions would edit the same files.
3. State the requested outcome and trace its real path through callers, persistence, scheduling, execution, and external
   interfaces as applicable. Check claims in old documents against source code.

## Development flow

1. **Design:** For a small, clear change, state the approach briefly and implement. For a larger feature, write a
   concise design in `docs/design/` covering desired behavior, boundaries, key contracts, risks, and an incremental
   rollout. Distinguish the target architecture from today's implementation. Unless the owner requires compatibility
   with a specific existing decision, recommend the cleanest sound design and explain a practical migration path.
2. **Plan:** Divide a larger feature into reviewable slices that each compile and have an observable outcome. Keep the
   plan with its design or in the task discussion. Avoid ceremonial plans for small changes.
3. **Implement:** Complete one slice, follow the affected behavior end to end, and inspect sibling paths for the same
   defect or contract. Keep unrelated refactoring separate. Do not start the next slice until the owner reviews and
   adjusts the current one, unless the owner asked for the whole feature at once.
4. **Verify:** The default gate for code is a successful compile of the affected Maven modules and their dependencies,
   including the repository's compile-time formatting, Checkstyle, and NullAway checks. Run focused additional checks
   when needed to resolve a concrete risk. Do not add or expand unit tests before asking the owner. Recommend a small
   number of high-value tests for critical state transitions, concurrency, permissions, data loss, or a
   hard-to-reproduce regression, and write them once authorized. Report any existing tests that fail or were not run;
   never claim unrun tests passed. Compilation does not establish runtime correctness.
5. **Hand off:** Summarize **Changed**, **Checked** (exact commands and results), **Review** (what to inspect or
   exercise), and **Next slice**. Call out assumptions and remaining risks. The owner reviews and may edit the diff
   before continuation.

Use JDK 21 (`mise.toml` selects it). Check `JAVA_HOME` when the shell defaults to another JDK. For a backend-only
change, a typical compile command is:

```bash
./mvnw -pl flink-platform-web -am compile
```

Replace `flink-platform-web` with the affected module when possible. A full-reactor Java-only build needs
`-Dskip.frontend=true` to skip the embedded frontend's npm build. For an API change, coordinate the contract with the
separate `runnelo-ui` project when relevant, but do not change that repository implicitly.

## Commits

When asked to commit, inspect the staged diff and use `type(scope): Capitalized imperative summary`
in English (for example, `docs(guidance): Refresh configuration and operations`). Choose a type
such as `feat`, `fix`, `refactor`, `docs`, or `chore`; scope it to the affected module or topic.
After a squash, rewrite the subject and any body to describe the combined diff. Leave unrelated
changes out of the commit.

## Project map

- `flink-platform-web`: Spring Boot APIs, Quartz scheduling, flow dispatch, execution, and worker lifecycle.
- `flink-platform-dao`: persisted entities (including `JobFlowDag` and `ExecutionConfig`), mappers, and database
  services.
- `flink-platform-common`: graph primitives, job vertices and edges, and shared enums; Java 8 source compatibility.
- `flink-platform-grpc`: distributed execution contract.
- `flink-platform-cron`, `flink-platform-alert`: scheduling helpers and alert integrations.
- `flink-platform-storage-*`, `flink-platform-environment-*`, `flink-platform-plugin-*`: adapters and integration
  boundaries.
- `flink-sql-submit-*`, `flink-platform-udf`: Flink-specific subprocess and UDF code; Java 8 source compatibility.
- `flink-platform-ui/frontend`: embedded legacy frontend, distinct from the separate `runnelo-ui` project.

The root POM and relevant module POMs are the source of truth for modules and build rules. Use
the [architecture and source map](docs/architecture.md) to locate the affected runtime path
and [configuration](docs/configuration.md) for profiles and infrastructure. API changes involving workspaces must trace
`X-Workspace-Id`, permission checks, and DAO tenant filtering. Verify documentation against code before changing it.

## Documentation

`docs/architecture.md`, `docs/configuration.md`, and `docs/startup.md` describe current behavior and setup.
Put proposed changes and migration paths in `docs/design/`; mark their implementation status.
Add only verified, reusable review lessons to `LESSONS.md`; promote recurring rules to
`docs/code-standards.md`.
