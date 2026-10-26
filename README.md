# Flink Platform Backend

A distributed platform for defining and running data workflows. It schedules DAGs through Quartz, persists definitions
and run state in MySQL, and dispatches work across peer nodes. Supported execution paths include Flink SQL/JAR, Shell,
and SQL tasks.

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
