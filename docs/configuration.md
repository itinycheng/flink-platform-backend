# Configuration

The backend uses Spring profiles. `flink-platform-web/src/main/resources/application.yml` selects
`dev` by default and imports the matching storage and Apollo files:

```yaml
spring:
  profiles:
    active: dev
  config:
    import:
      - classpath:storage-${spring.profiles.active}.yml
      - classpath:apollo-${spring.profiles.active}.properties
```

Use `-Dspring.profiles.active=docker` (before `-jar`) or the equivalent Spring environment
variable to select another profile.

## Profile-specific application settings

`application-dev.yml` and `application-docker.yml` configure:

- HTTP server port and Tomcat threads;
- authentication (`local`, `cas`, or `oidc`);
- the `master_platform` datasource;
- clustered Quartz backed by that datasource;
- the gRPC server and default client channel.

The gRPC properties live under `spring.grpc`, for example:

```yaml
spring:
  grpc:
    server:
      port: 9898
    client:
      default-channel:
        address: static://0.0.0.0:9898
        negotiationType: plaintext
```

Do not commit production database or identity-provider credentials. The Docker profile already
reads database credentials from `DB_HOST`, `DB_USER`, and `DB_PASSWORD`; use the same externalized
configuration pattern for other secrets.

## Worker settings

The shared defaults are in `application.yml` and bind to `WorkerConfig`:

```yaml
worker:
  flowExecThreads: 500
  perFlowExecThreads: 5
  maxPerFlowExecThreads: 10
  maxActiveRunsPerFlow: 10
  errorRetries: 3
  reactiveExecThreads: 10
  flinkSubmissionTimeout: 5m
  maxShellExecutionTimeout: 8h
  flowWaitTimeout: 12h
  flowWaitWarningAfter: 1h
```

- `flowExecThreads`: maximum flow runs this worker executes concurrently.
- `perFlowExecThreads`: default parallel job limit inside one flow run.
- `maxPerFlowExecThreads`: platform ceiling for the per-run job limit.
- `maxActiveRunsPerFlow`: platform ceiling for concurrently active runs of one flow.
- `flowWaitTimeout`: fallback dependency-wait deadline for scheduleless runs or cron expressions
  with no next fire.
- `flowWaitWarningAfter`: delay before the in-memory, one-shot WAITING warning. The warning is
  skipped when the run's deadline occurs sooner.

Spring duration syntax such as `20s`, `5m`, and `12h` is accepted. `WorkerConfig` validates the
minimums and limits during startup.

## Storage

`storage-dev.yml` and `storage-docker.yml` bind to `StorageProperties`:

```yaml
storage:
  type: hdfs
  basePath: flink-platform
  backends:
    local:
      workDir: /app/data
    hdfs:
      "[hadoop.username]": hdfs
      "[fs.defaultFS]": hdfs://namenode:8020
    s3:
      bucket: datacenter
      region: us-east-1
      endpoint: http://minio:9000
      path-style-access: true
      access-key: ${AWS_ACCESS_KEY_ID:}
      secret-key: ${AWS_SECRET_ACCESS_KEY:}
```

`storage.type` selects one active backend. All backend blocks may coexist; only the selected block
is loaded. `storage.basePath` is relative to that backend's namespace root. HDFS map keys containing
dots must use bracket notation so Spring treats each one as a single key.

## Apollo

`apollo-dev.properties` and `apollo-docker.properties` contain the client metadata. Apollo
bootstrap is disabled in the shipped files. To enable it, configure the correct metadata endpoint
and salt, then enable the bootstrap properties for the required namespaces. Do not reuse the
development salt in production.
