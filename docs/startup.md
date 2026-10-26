# Local backend startup

The backend runs with JDK 21 and MySQL. Running a workflow also requires storage and the external
services for its job type.

1. Create a **new development database** and inspect [schema.sql](sql/schema.sql) before importing
   it. The file contains Quartz tables, platform tables, and seed data, and it drops existing
   tables. For an existing database, review [migration.sql](sql/migration.sql) and the current
   schema instead of replaying the full schema file.
2. Configure the `dev` profile for your machine in
   `flink-platform-web/src/main/resources/application-dev.yml`. Check the `master_platform` MySQL
   datasource, authentication, HTTP and gRPC ports. Configure the selected storage backend in
   `flink-platform-storage/flink-platform-storage-base/src/main/resources/storage-dev.yml`. Keep
   machine-specific credentials out of commits. See [configuration](configuration.md) for settings.
3. Select JDK 21 (`mise.toml` requests it), then compile the backend and its reactor dependencies:

   ```bash
   ./mvnw -pl flink-platform-web -am compile
   ```

   The embedded legacy frontend is outside this reactor subset. The web module still copies any
   existing `flink-platform-ui/frontend/dist` assets. This command does not build the separate
   Runnelo UI project.

4. Start `com.flink.platform.web.PlatformWebApplication` from the IDE with the `dev` profile. The
   checked-in dev settings use HTTP port `9104` and gRPC port `9898`. MySQL must be reachable;
   verify storage and execution services before running workflows.

The separate [Runnelo UI](https://github.com/itinycheng/runnelo-ui) has its own
[development instructions](https://github.com/itinycheng/runnelo-ui#quick-start). Its default
development mode uses mocks; running it does not verify backend integration.
