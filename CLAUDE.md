# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build (skip tests)
./mvnw clean package -DskipTests

# Build with frontend skipped (CI mode)
./mvnw clean verify -Dskip.frontend=true

# Run all tests
./mvnw test

# Run tests in a specific module
./mvnw test -pl flink-platform-web
./mvnw test -pl flink-platform-common

# Run a specific test class
./mvnw test -Dtest=JobFlowTest
./mvnw test -pl flink-platform-web -Dtest=JobFlowRunServiceTest

# Start the application
java -Xms4g -Xmx4g -jar -Dspring.profiles.active=dev flink-platform-web/target/flink-platform-web-0.0.1.jar

# Docker (MySQL included)
docker-compose up -d --build
```

Web UI is served on port **9104**, gRPC on port **9898**.

## Code Quality Tools

The project enforces Checkstyle, Error Prone, NullAway, and Spotless (Palantir Java Format) at build time:

```bash
# Check formatting
./mvnw spotless:check

# Apply formatting
./mvnw spotless:apply
```

Config files are in `tools/maven/checkstyle.xml` and `tools/maven/suppressions.xml`.

## Architecture Overview

This is a **distributed, centerless job scheduling platform** for Apache Flink workflows. All deployed instances are equal workers — there is no dedicated master node. Coordination is handled via MySQL (Quartz JDBC store) and distributed locking (ShedLock).

### Module Layout

| Module | Role |
|---|---|
| `flink-platform-common` | Shared enums, utils, DAG graph model (`DAG<K,V,E>`, `JobFlowDag`) |
| `flink-platform-dao` | MyBatis-Plus entities and mappers; dynamic multi-datasource |
| `flink-platform-web` | Main Spring Boot app: REST API, Quartz scheduler, gRPC server |
| `flink-platform-grpc` | Protobuf definitions + gRPC service stubs for distributed execution |
| `flink-platform-cron` | Cron scheduling and job monitoring logic |
| `flink-platform-alert` | Alerting integrations (Email, DingTalk, FeiShu, SMS) |
| `flink-platform-storage-*` | Storage abstraction with HDFS and local filesystem implementations |
| `flink-platform-plugin-apollo` | Apollo dynamic config plugin (active by default in web) |
| `flink-sql-submit-{1.12,1.15}` | Version-specific Flink SQL submission (Java 8; run as subprocess) |
| `flink-platform-udf` | Flink UDFs (Java 8) |

### Job Execution Flow

1. **Quartz scheduler** triggers a `JobFlowQuartzJob` at the configured cron time.
2. `ProcessJobService` walks the `JobFlowDag` (a typed DAG of `JobVertex`/`JobEdge`) and enqueues jobs respecting dependencies.
3. Each `JobVertex` maps to a `JobType` (e.g., `FLINK_SQL`, `SHELL`, `CLICKHOUSE_SQL`). The appropriate handler runs the job — often by spawning a subprocess (`flink-sql-submit-*` JARs) or executing a gRPC call to another node.
4. Status is persisted to `t_job_run` / `t_job_flow_run` via the DAO layer.
5. On node restart, `InitJobFlowScheduler` recovers in-flight workflows from the database.

### DAG Model

`JobFlowDag` (in `flink-platform-common`) extends `DAG<Long, JobVertex, JobEdge>`. It is serialized as JSON into the `t_job_flow` table using MyBatis-Plus's `Jackson3TypeHandler`. `JobFlowDagHelper` provides traversal utilities. Execution strategies (`ONLY_CUR_JOB`, `ALL_PRE_JOBS`, `ALL_POST_JOBS`) control which upstream/downstream jobs run on a partial re-execution.

### gRPC

The `flink-platform-grpc` module defines protobuf messages and a `JobService`. The web module runs both a gRPC server (`JobGrpcServer`) and client (`JobGrpcClient`), enabling any node to delegate job execution to any peer.

### Configuration Profiles

Spring profiles (`dev`, `test`, `prod`) are selected at startup. The web module imports `storage-${spring.profiles.active}.yml` from `flink-platform-storage-base`. Apollo config plugin is active by default and can supply runtime configuration overrides.

Key config files:
- `flink-platform-web/src/main/resources/application.yml` — base config
- `flink-platform-web/src/main/resources/application-dev.yml` — dev-specific (ports, DB, Flink paths, thread pools)
- `flink-platform-storage/flink-platform-storage-base/src/main/resources/storage-dev.yml` — storage backend config

### Key Entity–Table Mapping

| Entity | Table | Purpose |
|---|---|---|
| `JobInfo` | `t_job` | Job configuration |
| `JobRunInfo` | `t_job_run` | Per-execution job state |
| `JobFlow` | `t_job_flow` | Workflow definition (includes serialized DAG) |
| `JobFlowRun` | `t_job_flow_run` | Per-execution workflow state |
| `CatalogInfo` | `t_catalog_info` | Flink catalog config |
| `Resource` | `t_resource` | Uploaded JARs / UDF files |

Schema SQL lives in `docs/sql/schema.sql`.

### Java Version Notes

The root project targets Java 21. However, `flink-sql-submit-*` and `flink-platform-udf` must compile to Java 8 (`<maven.compiler.source>8</maven.compiler.source>`) because they run inside Flink's classloader. Do not use Java 9+ APIs in those modules.

### Job Handler Extension Point

Adding a new `JobType` requires three pieces:

1. **`BaseJob` subclass** — job-type-specific configuration fields, stored as JSON in `t_job.config`. Register it in `BaseJob`'s `@JsonSubTypes` annotation (uses `type` discriminator field).
2. **`CommandBuilder` implementation** — converts `JobRunInfo` → `JobCommand`. Must implement `isSupported(JobType, String version)` for version-aware dispatch.
3. **`CommandExecutor` implementation** — runs the `JobCommand` and returns a `JobCallback` (status, appId, jobId, tracking URL). Store active tasks in the global `RUNNING_MAP` so `CommandMonitor` can enforce timeouts.

`ProcessJobService` (central orchestrator) picks the matching builder/executor by iterating registered Spring beans. See existing implementations (`FlinkCommandBuilder`, `SqlCommandExecutor`, etc.) for reference.

### Polymorphic Job Configuration

`JobInfo.config` and `JobRunInfo.config` are deserialized from JSON to `BaseJob` subclasses via `Jackson3TypeHandler`. The `type` field in the JSON (matching `JobType` name) drives Jackson's `@JsonSubTypes` dispatch. When reading entities from the DB, the concrete subtype is resolved automatically.

### Variable Interpolation

Job subjects and configs support runtime variable substitution:
- `${time:yyyyMMdd}` / `${time:yyyyMMdd[curDate-3d]}` — time-based variables
- `${jobRun:id}` — references the current job run's id
- `${setParam:key=value}` — sets a parameter for downstream jobs in the flow

### Exception Patterns

- **`DefinitionException`** — wraps a `ResponseStatus` enum value; thrown for business/user errors; caught by `ApiExceptionHandler` and returned as a structured `ResultInfo` response.
- **`UncaughtException`** — internal errors that propagate through (used for gRPC client-side error propagation).

### Testing Conventions

Tests use JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`). There is no `@SpringBootTest`; all tests are lightweight unit tests with manual mocking via `@Mock`/`@InjectMocks` and `ReflectionTestUtils.setField()` for private field injection. Instantiate entities directly (`new JobRunInfo()`) rather than using a test container or application context.