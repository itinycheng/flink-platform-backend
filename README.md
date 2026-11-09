# Flink Platform Backend

A distributed platform for defining and running data workflows. It schedules DAGs through Quartz, persists definitions
and run state in MySQL, and dispatches work across peer nodes. Supported execution paths include Flink SQL/JAR, Shell,
and SQL tasks.

## Run locally with Docker

Install Docker with Compose, initialize the embedded frontend submodule with
`git submodule update --init --recursive`, and check `DEV_USER` and `DEV_PASSWORD` in the root `.env`.
Run one command from the repository root:

| Profile | Start command                                  | Storage                            |
|---------|------------------------------------------------|------------------------------------|
| `local` | `docker compose --profile local up --build -d` | `docker/local/`                    |
| `s3`    | `docker compose --profile s3 up --build -d`    | MinIO (`9000` API, `9001` console) |
| `hdfs`  | `docker compose --profile hdfs up --build -d`  | HDFS (`9870` NameNode UI)          |

Each profile packages the full Maven reactor, including the embedded legacy frontend, then starts MySQL and the
backend. Open
[http://localhost:9104/actuator/health](http://localhost:9104/actuator/health) and look for `"status":"UP"`. Other
shared ports are `3306` (MySQL), `9898` (gRPC), and `5005` (Java debugging).
For `s3`, open the [MinIO console](http://localhost:9001) and sign in with the credentials in `.env`.

For status, logs, and shutdown, use `docker compose --profile <profile> ps`,
`docker compose --profile <profile> logs -f web-<profile>`, and `docker compose --profile <profile> down` (replace
`<profile>` with `local`, `s3`, or `hdfs`). Stop one profile before starting another; stored resources are not copied
between backends.

Use a fresh development database: MySQL imports [schema.sql](docs/sql/schema.sql) only on first initialization. The
script contains `DROP TABLE` statements; do not run it against an existing database. The `docker` profile sets
`spring.quartz.jdbc.initialize-schema=always`, so the web container drops and recreates the Quartz tables on every
start and scheduled state does not survive a restart. Catalog definitions are configured after startup because their
SQL depends on the target environment.
Existing Docker data directories remain under `docker/` after `down`. For HDFS profile switches, see the note in
[docker-compose.yaml](docker-compose.yaml).

## Repository layout

| Area                                                       | Responsibility                                                       |
|------------------------------------------------------------|----------------------------------------------------------------------|
| `flink-platform-web`                                       | Spring Boot API, workflow scheduling and execution, worker lifecycle |
| `flink-platform-common`, `flink-platform-dao`              | Shared workflow models and database access                           |
| `flink-platform-grpc`                                      | Cross-node execution contract                                        |
| `flink-platform-storage-*`, `flink-platform-environment-*` | Storage and execution environment adapters                           |
| `flink-sql-submit-*`, `flink-platform-udf`                 | Flink-specific submission and UDF code                               |
| `flink-platform-ui/frontend`                               | Embedded legacy frontend                                             |

The separate [Runnelo UI](https://github.com/itinycheng/runnelo-ui) project is the current frontend development
reference. It uses mocks while its new backend contract is being defined; its existing API shapes should not be assumed
to match this backend.

## Compile

Use JDK 21 and the Maven wrapper; `mise.toml` selects Java 21, but an existing `JAVA_HOME` may still point to another
JDK. For a backend-only change:

```bash
./mvnw -pl flink-platform-web -am compile
```

This builds the web module and required reactor modules, including the configured formatting, Checkstyle, and NullAway
checks. The embedded frontend is outside this reactor subset. For a full-reactor Java-only build, pass
`-Dskip.frontend=true`.

`flink-platform-common`, `flink-platform-udf`, and `flink-sql-submit-*` compile to Java 8 bytecode;
check their POMs before using newer Java APIs.

For local setup, see [startup](docs/startup.md). The [architecture](docs/architecture.md) includes a source map for
the main runtime paths; [configuration](docs/configuration.md) explains profiles and infrastructure settings.

## AI-assisted development

[AGENTS.md](AGENTS.md) defines the daily agent workflow and links the relevant coding rules and project context.

## License

[Apache 2.0](LICENSE)
