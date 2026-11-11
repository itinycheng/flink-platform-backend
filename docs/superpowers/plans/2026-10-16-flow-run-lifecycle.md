# Flow-Run Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Centralize non-`JOB_LIST` `t_job_flow_run` lifecycle transitions, make distributed kill and completion races deterministic, and add the minimum job-run launch fence required for safe cleanup.

**Architecture:** New lifecycle orchestration lives in `com.flink.platform.web.flowrun`; conditional persistence remains in `flink-platform-dao`. The database arbitrates state and host ownership with bounded CAS operations. `JOB_LIST` remains on its existing legacy path, while every other current or future flow type uses the type-independent lifecycle contract.

**Tech Stack:** Java 21, Spring Boot 4, MyBatis-Plus, MySQL/PostgreSQL, JUnit 5, Mockito, Testcontainers 2.0.5, Maven.

**Spec:** `docs/design/flow-run-state-machine.md` and `docs/design/flow-run-state-machine.zh-CN.md`

## Global Constraints

- Keep existing `ExecutionStatus` codes and the `t_job_flow_run` schema unchanged.
- Preserve existing REST response shapes; the only deliberate semantic change is that committed terminal states are immutable.
- Route `JOB_LIST` through its legacy path before entering `web.flowrun`; add no new `JOB_LIST` behavior.
- Keep lifecycle policy independent of `JobFlowType`; future `FLINK`, `SPARK`, `SQL`, and renamed `FLOW` use the same contract.
- Every status write names expected source state; owner-sensitive writes also name expected host.
- Each business operation performs at most one CAS and one reconciliation read per target row.
- Retry whole transactions only at the outer business-operation boundary, at most three total attempts.
- Use TDD for every task and commit after each independently passing slice.
- Do not use a generic state-machine framework, event sourcing, database triggers, or a new owner-generation column.

---

## File Structure

**Create under `flink-platform-web/src/main/java/com/flink/platform/web/flowrun/`:**

- `FlowRunTransition.java` — compact transition result and outcome enum.
- `FlowRunStateService.java` — legal lifecycle operations and CAS-result reconciliation.
- `FlowRunAdmissionService.java` — transactional creation/admission and wait deadlines.
- `FlowRunKillService.java` — parent kill intent, child cleanup, and finalization.

**Keep in existing layers:**

- `flink-platform-dao/.../JobFlowRunService.java` — conditional state, activation, owner-transfer, and state-read primitives.
- `flink-platform-dao/.../JobFlowRunMapper.java` — row-lock projection used by child reservation.
- `web.service.KillJobService` — job-run kill only after flow-run methods move out.
- `web.runner.FlowRunDispatcher` / `FlowExecuteThread` — polling and execution callers, not policy owners.
- `web.quartz.JobFlowRunner` — type routing; `JOB_LIST` exits through the legacy branch.

---

### Task 1: Flow-Run Persistence Primitives

**Files:**
- Modify: `flink-platform-dao/src/main/java/com/flink/platform/dao/service/JobFlowRunService.java`
- Modify: `flink-platform-dao/src/main/java/com/flink/platform/dao/mapper/JobFlowRunMapper.java`
- Modify: `flink-platform-web/src/test/java/com/flink/platform/web/service/JobFlowRunServiceTest.java`

**Interfaces:**
- Produces: `updateStatusFromAnyTo(Long, Collection<ExecutionStatus>, @Nullable String, ExecutionStatus, LocalDateTime): int`
- Produces: `updateCreatedCandidate(JobFlowRun): int`
- Produces: `updateHostIfOwnedBy(Long, String, String, Collection<ExecutionStatus>): int`
- Produces: `findState(Long): @Nullable JobFlowRun`
- Produces: `findStateForUpdate(Long): @Nullable JobFlowRun`, backed by mapper `selectStateForUpdate`.
- Preserves: `updateLegacyStatusById(Long, ExecutionStatus, LocalDateTime): boolean` for bridge-disabled mode.

- [ ] **Step 1: Write failing DAO-service tests for status timestamps and predicates**

Add tests that capture the entity and wrapper passed to `JobFlowRunMapper.update`:

```java
@Test
void runningTransitionSetsStartTimeAndRequiresStatusAndHost() {
    var entity = ArgumentCaptor.forClass(JobFlowRun.class);
    var wrapper = ArgumentCaptor.forClass(Wrapper.class);
    when(jobFlowRunMapper.update(entity.capture(), wrapper.capture())).thenReturn(1);

    var changed = jobFlowRunService.updateStatusFromAnyTo(
            7L, Set.of(SUBMITTED), "worker-a", RUNNING, NOW);

    assertEquals(1, changed);
    assertEquals(RUNNING, entity.getValue().getStatus());
    assertEquals(NOW, entity.getValue().getStartTime());
    assertNull(entity.getValue().getEndTime());
    assertTrue(wrapper.getValue().getSqlSegment().contains("status"));
    assertTrue(wrapper.getValue().getSqlSegment().contains("host"));
}

@Test
void terminalTransitionSetsEndTimeButNotStartTime() {
    var entity = ArgumentCaptor.forClass(JobFlowRun.class);
    when(jobFlowRunMapper.update(entity.capture(), any())).thenReturn(1);
    assertEquals(1, jobFlowRunService.updateStatusFromAnyTo(
            7L, Set.of(WAITING), null, FAILURE, NOW));
    assertEquals(NOW, entity.getValue().getEndTime());
    assertNull(entity.getValue().getStartTime());
}
```

- [ ] **Step 2: Run the focused test and confirm compilation/test failure**

Run:

```bash
./mvnw -pl flink-platform-web -am -Dtest=JobFlowRunServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the four persistence methods do not exist.

- [ ] **Step 3: Implement the conditional persistence methods**

Use the existing MyBatis-Plus update pattern and keep business validation out of DAO code:

```java
public int updateStatusFromAnyTo(
        Long flowRunId,
        Collection<ExecutionStatus> expectedStatuses,
        @Nullable String expectedHost,
        ExecutionStatus target,
        LocalDateTime transitionTime) {
    if (flowRunId == null || expectedStatuses.isEmpty()) {
        return 0;
    }
    var update = new JobFlowRun();
    update.setStatus(target);
    if (target == RUNNING) {
        update.setStartTime(transitionTime);
    }
    if (target.isTerminalState()) {
        update.setEndTime(transitionTime);
    }
    return baseMapper.update(update, new QueryWrapper<JobFlowRun>()
            .lambda()
            .eq(JobFlowRun::getId, flowRunId)
            .in(JobFlowRun::getStatus, expectedStatuses)
            .eq(expectedHost != null, JobFlowRun::getHost, expectedHost));
}

public int updateCreatedCandidate(JobFlowRun candidate) {
    return baseMapper.update(candidate, new QueryWrapper<JobFlowRun>()
            .lambda()
            .eq(JobFlowRun::getId, candidate.getId())
            .eq(JobFlowRun::getStatus, CREATED));
}
```

Implement owner transfer with `id + old host + non-terminal statuses`, and make `findState` select only `id/status/host/startTime/endTime`. Add mapper `selectStateForUpdate` with `FOR UPDATE` and expose it as `findStateForUpdate`. Add `updateLegacyStatusById` as the only unrestricted bridge helper; it sets the supplied transition time consistently and is deprecated from its first commit.

- [ ] **Step 4: Add tests for atomic activation, owner transfer, missing state, and database-error propagation**

```java
@Test
void createdCandidateUpdateRequiresCreatedSource() {
    var candidate = candidate(9L, SUBMITTED);
    when(jobFlowRunMapper.update(eq(candidate), any())).thenReturn(1);
    assertEquals(1, jobFlowRunService.updateCreatedCandidate(candidate));
}

@Test
void findStateDoesNotSwallowDatabaseFailure() {
    when(jobFlowRunMapper.selectOne(any())).thenThrow(new DataAccessResourceFailureException("db"));
    assertThrows(DataAccessResourceFailureException.class, () -> jobFlowRunService.findState(9L));
}
```

- [ ] **Step 5: Run tests, format, and commit**

```bash
./mvnw -pl flink-platform-web -am -Dtest=JobFlowRunServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw spotless:apply
git add flink-platform-dao/src/main/java/com/flink/platform/dao/service/JobFlowRunService.java \
  flink-platform-dao/src/main/java/com/flink/platform/dao/mapper/JobFlowRunMapper.java \
  flink-platform-web/src/test/java/com/flink/platform/web/service/JobFlowRunServiceTest.java
git commit -m "feat(dao): add flow-run conditional persistence"
```

---

### Task 2: `web.flowrun` Transition Contract

**Files:**
- Create: `flink-platform-web/src/main/java/com/flink/platform/web/flowrun/FlowRunTransition.java`
- Create: `flink-platform-web/src/main/java/com/flink/platform/web/flowrun/FlowRunStateService.java`
- Create: `flink-platform-web/src/test/java/com/flink/platform/web/flowrun/FlowRunStateServiceTest.java`

**Interfaces:**
- Consumes: Task 1 persistence methods.
- Produces: `FlowRunTransition(Outcome, @Nullable ExecutionStatus)` with `APPLIED`, `REJECTED`, `NOT_FOUND`.
- Produces: domain operations `activateCreated`, `rejectCreated`, `submitWaiting`, `failWaiting`, `failSubmitted`, `startExecution`, `completeExecution`, `requestKill`, `confirmKilled`, `forceKilled`, and `reassignHost`.

- [ ] **Step 1: Write parameterized failing tests for the public state contract**

```java
@ParameterizedTest
@CsvSource({"SUCCESS", "FAILURE", "EXPECTED_FAILURE"})
void completeExecutionAcceptsOnlyNormalTerminalStatuses(ExecutionStatus target) {
    when(flowRunService.updateStatusFromAnyTo(5L, Set.of(RUNNING), "worker-a", target, NOW))
            .thenReturn(1);
    assertEquals(APPLIED, stateService.completeExecution(5L, "worker-a", target).outcome());
}

@Test
void rejectedCasReadsCommittedWinnerOnce() {
    when(flowRunService.updateStatusFromAnyTo(anyLong(), any(), any(), any(), any())).thenReturn(0);
    when(flowRunService.findState(5L)).thenReturn(state(5L, KILLING, "worker-a"));
    var result = stateService.completeExecution(5L, "worker-a", SUCCESS);
    assertEquals(REJECTED, result.outcome());
    assertEquals(KILLING, result.status());
    verify(flowRunService, times(1)).findState(5L);
}
```

Also cover every allowed edge, invalid completion targets, missing rows, idempotent `KILLING/KILLED`, and terminal immutability.

- [ ] **Step 2: Run the test and verify failure**

```bash
./mvnw -pl flink-platform-web -am -Dtest=FlowRunStateServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because `web.flowrun` does not exist.

- [ ] **Step 3: Implement the compact result type**

```java
@NullMarked
public record FlowRunTransition(Outcome outcome, @Nullable ExecutionStatus status) {
    public enum Outcome { APPLIED, REJECTED, NOT_FOUND }

    public static FlowRunTransition applied(ExecutionStatus status) {
        return new FlowRunTransition(Outcome.APPLIED, status);
    }

    public static FlowRunTransition rejected(ExecutionStatus status) {
        return new FlowRunTransition(Outcome.REJECTED, status);
    }

    public static FlowRunTransition notFound() {
        return new FlowRunTransition(Outcome.NOT_FOUND, null);
    }
}
```

- [ ] **Step 4: Implement the state service with one shared CAS helper**

```java
private FlowRunTransition transition(
        long id,
        Set<ExecutionStatus> expected,
        @Nullable String expectedHost,
        ExecutionStatus target) {
    var changed = flowRunService.updateStatusFromAnyTo(
            id, expected, expectedHost, target, LocalDateTime.now());
    if (changed == 1) {
        return FlowRunTransition.applied(target);
    }
    var state = flowRunService.findState(id);
    return state == null
            ? FlowRunTransition.notFound()
            : FlowRunTransition.rejected(state.getStatus());
}
```

Validate method arguments before SQL. Implement `requestKill` as one state read followed by at most one CAS: direct `KILLED` for `CREATED/WAITING/SUBMITTED`, `KILLING` for `RUNNING`, idempotent result for `KILLING/KILLED`, and rejection for other terminal states.

- [ ] **Step 5: Run focused tests and commit**

```bash
./mvnw -pl flink-platform-web -am -Dtest=FlowRunStateServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw spotless:apply
git add flink-platform-web/src/main/java/com/flink/platform/web/flowrun \
  flink-platform-web/src/test/java/com/flink/platform/web/flowrun/FlowRunStateServiceTest.java
git commit -m "feat(web): add flow-run lifecycle contract"
```

---

### Task 3: Real-Database CAS Concurrency Contract

**Files:**
- Modify: `pom.xml`
- Modify: `flink-platform-web/pom.xml`
- Create: `flink-platform-web/src/test/java/com/flink/platform/web/flowrun/FlowRunCasConcurrencyTest.java`

**Interfaces:**
- Consumes: Task 1 SQL contract.
- Produces: identical MySQL/PostgreSQL evidence for same-row state and host races.

- [ ] **Step 1: Add managed test dependencies**

Add `testcontainers-mysql` and `testcontainers-postgresql` at `${testcontainers.version}` to root dependency management, then add them plus test-scoped PostgreSQL JDBC to `flink-platform-web/pom.xml`.

- [ ] **Step 2: Write the failing two-connection concurrency test**

Create a minimal portable table in each container and race two transactions:

```java
private int cas(Connection connection, String from, String to) throws SQLException {
    try (var statement = connection.prepareStatement(
            "UPDATE t_job_flow_run SET status = ? WHERE id = ? AND status = ?")) {
        statement.setString(1, to);
        statement.setLong(2, 1L);
        statement.setString(3, from);
        return statement.executeUpdate();
    }
}

assertEquals(1, successCount.get());
assertTrue(Set.of("SUCCESS", "KILLING").contains(selectStatus(dataSource)));
```

Run the same helper against `org.testcontainers.mysql.MySQLContainer` and `org.testcontainers.postgresql.PostgreSQLContainer`. Add cases for terminal overwrite rejection, `KILLING` overwrite rejection, and old-host completion after owner transfer.

- [ ] **Step 3: Run the integration test and inspect the failure before completing setup**

```bash
./mvnw -pl flink-platform-web -am -Dtest=FlowRunCasConcurrencyTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected before dependency/schema setup is complete: compilation or container initialization failure. After setup: all cases PASS on both databases.

- [ ] **Step 4: Add the parent-row reservation-versus-kill race**

Transaction A locks the parent row and inserts a child marker before commit. Transaction B attempts `RUNNING → KILLING`. Assert either the child commits before `KILLING`, or `KILLING` commits and the child insert is rejected after the lock is released; no outcome permits a child created after the committed fence.

- [ ] **Step 5: Commit the portable concurrency harness**

```bash
git add pom.xml flink-platform-web/pom.xml \
  flink-platform-web/src/test/java/com/flink/platform/web/flowrun/FlowRunCasConcurrencyTest.java
git commit -m "test(web): verify flow-run CAS on mysql and postgres"
```

---

### Task 4: Transactional Flow-Run Admission

**Files:**
- Create: `flink-platform-web/src/main/java/com/flink/platform/web/flowrun/FlowRunAdmissionService.java`
- Create: `flink-platform-web/src/test/java/com/flink/platform/web/flowrun/FlowRunAdmissionServiceTest.java`
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/service/FlowScheduleService.java`
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/quartz/JobFlowRunner.java`
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/runner/FlowRunDispatcher.java`
- Remove after migration: `flink-platform-web/src/test/java/com/flink/platform/web/service/FlowScheduleServiceTest.java`

**Interfaces:**
- Consumes: `FlowRunStateService.failWaiting`, `rejectCreated`, `activateCreated`.
- Produces: `resolveWaitDeadline(JobFlowRun): LocalDateTime` and `tryCreateFlowRun(JobFlowRun): FlowRunCreationResult`.

- [ ] **Step 1: Move existing admission tests without changing behavior**

Move the current wait-deadline and conflict tests to `FlowRunAdmissionServiceTest`, instantiate the new service, and run them red because the class does not exist.

- [ ] **Step 2: Add failing tests for bounded CAS-loss reconciliation**

```java
@Test
void terminalWinnerAllowsFreshCandidateAfterExpiredWaitCasLoss() {
    when(stateService.failWaiting(1L)).thenReturn(rejected(SUCCESS));
    when(flowRunService.listActiveRuns(FLOW_ID, null)).thenReturn(List.of(expired), List.of());
    assertEquals(CREATED, admission.tryCreateFlowRun(candidate).state());
}

@Test
void activeWinnerBlocksAfterSingleFreshSnapshot() {
    when(stateService.failWaiting(1L)).thenReturn(rejected(SUBMITTED));
    when(flowRunService.listActiveRuns(FLOW_ID, null))
            .thenReturn(List.of(expired), List.of(submitted));
    assertEquals(BLOCKED, admission.tryCreateFlowRun(candidate).state());
    verify(flowRunService, times(2)).listActiveRuns(FLOW_ID, null);
}
```

- [ ] **Step 3: Extract admission from `FlowScheduleService`**

Keep Quartz `scheduleJob/unscheduleJob` in `FlowScheduleService`. Move wait deadlines and `tryCreateFlowRun` into `FlowRunAdmissionService`, preserving `READ_COMMITTED`, the definition-row lock, and post-commit alert return data.

For a pre-created candidate call `stateService.activateCreated(candidate)`; for a fresh candidate call `flowRunService.save(candidate)`. Never use `saveOrUpdate` for a reserved row.

- [ ] **Step 4: Keep `JOB_LIST` before the new package boundary**

In `JobFlowRunner`, retain the current `JOB_LIST` branch before `FlowRunAdmissionService.tryCreateFlowRun`. Non-`JOB_LIST` types use the new service without a type switch inside `web.flowrun`.

- [ ] **Step 5: Migrate waiting-run transitions and winner-only alerts**

Replace dispatcher calls to `JobFlowRunService.updateStatusFromTo` with `stateService.submitWaiting` and `stateService.failWaiting`. A failed wait sends its alert only for `APPLIED`; `REJECTED` and `NOT_FOUND` emit no terminal side effect. Add a test where timeout loses to `KILLED` and verify `alertSendingService` is not invoked.

- [ ] **Step 6: Add bounded whole-transaction retries**

Wrap only the admission transaction in a `TransactionTemplate` loop. Start a fresh transaction for each attempt and retry `CannotAcquireLockException`, `DeadlockLoserDataAccessException`, and `TransientDataAccessResourceException` at most twice after the initial attempt. Do not retry `FlowRunTransition.REJECTED` or any other business result:

```java
for (var attempt = 1; attempt <= 3; attempt++) {
    try {
        return transactionTemplate.execute(status -> tryCreateOnce(candidate));
    } catch (TransientDataAccessException e) {
        if (attempt == 3) {
            throw e;
        }
        ThreadUtil.safeSleep(ThreadLocalRandom.current().nextLong(10, 51));
    }
}
throw new IllegalStateException("Unreachable admission retry state");
```

Add tests proving a deadlock succeeds on attempt three, attempt four never occurs, and a business block executes once.

- [ ] **Step 7: Update dispatcher wait-deadline dependency and run tests**

```bash
./mvnw -pl flink-platform-web -am \
  -Dtest=FlowRunAdmissionServiceTest,JobFlowRunnerTest,FlowRunDispatcherTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw spotless:apply
```

- [ ] **Step 8: Commit**

```bash
git add flink-platform-web/src/main/java/com/flink/platform/web/flowrun/FlowRunAdmissionService.java \
  flink-platform-web/src/main/java/com/flink/platform/web/service/FlowScheduleService.java \
  flink-platform-web/src/main/java/com/flink/platform/web/quartz/JobFlowRunner.java \
  flink-platform-web/src/main/java/com/flink/platform/web/runner/FlowRunDispatcher.java \
  flink-platform-web/src/test/java/com/flink/platform/web/flowrun/FlowRunAdmissionServiceTest.java \
  flink-platform-web/src/test/java/com/flink/platform/web/service/FlowScheduleServiceTest.java
git commit -m "refactor(web): centralize flow-run admission"
```

---

### Task 5: Flow-Run Execution Ownership and Completion

**Files:**
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/runner/FlowRunDispatcher.java`
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/runner/FlowExecuteThread.java`
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/lifecycle/WorkerHeartbeat.java`
- Modify: `flink-platform-web/src/test/java/com/flink/platform/web/runner/FlowRunDispatcherTest.java`
- Create: `flink-platform-web/src/test/java/com/flink/platform/web/runner/FlowExecuteThreadTest.java`
- Create: `flink-platform-web/src/test/java/com/flink/platform/web/lifecycle/WorkerHeartbeatTest.java`

**Interfaces:**
- Consumes: `startExecution`, `completeExecution`, `failSubmitted`, `reassignHost`.
- Produces: `SUBMITTED` first-start claim, same-host `RUNNING` recovery, and host-fenced completion.

- [ ] **Step 1: Write failing executor claim and recovery tests**

```java
@Test
void submittedRunStartsOnlyWhenClaimIsApplied() {
    when(stateService.startExecution(RUN_ID, HOST_IP)).thenReturn(rejected(KILLED));
    thread.run();
    verifyNoInteractions(jobThreadFactory);
}

@Test
void runningRunOnLocalHostUsesRecoveryPathWithoutSecondTransition() {
    run.setStatus(RUNNING);
    run.setHost(HOST_IP);
    thread.run();
    verify(stateService, never()).startExecution(anyLong(), anyString());
}
```

Refactor `FlowExecuteThread` constructor to receive testable collaborators from `FlowRunCoordinator` instead of resolving new state dependencies through `SpringContext`. Add a package-private nested `JobThreadFactory` functional interface whose production implementation returns `new JobExecuteThread(flowRunId, vertex)`.

- [ ] **Step 2: Implement first-start versus resume branching**

```java
private boolean acquireExecution() {
    return switch (jobFlowRun.getStatus()) {
        case SUBMITTED -> stateService.startExecution(jobFlowRun.getId(), HOST_IP).outcome() == APPLIED;
        case RUNNING -> HOST_IP.equals(jobFlowRun.getHost());
        default -> false;
    };
}
```

Only an applied completion transition sends the normal terminal alert. A rejected completion at `KILLING/KILLED` returns silently.

- [ ] **Step 3: Replace no-vertex unconditional failure**

In `FlowRunCoordinator`, use `failSubmitted` for a `SUBMITTED` row and host-fenced `completeExecution(..., FAILURE)` for a recovered `RUNNING` row. Send the failure alert only for `APPLIED`.

- [ ] **Step 4: Make orphan reassignment conditional per row**

Replace `updateBatchById` in `WorkerHeartbeat` with `stateService.reassignHost(id, deadHost, targetHost)`. Add tests that a false result emits no ownership side effect and that terminal/changed-host rows are preserved.

- [ ] **Step 5: Run tests and commit**

```bash
./mvnw -pl flink-platform-web -am \
  -Dtest=FlowRunDispatcherTest,FlowExecuteThreadTest,WorkerHeartbeatTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw spotless:apply
git add flink-platform-web/src/main/java/com/flink/platform/web/runner \
  flink-platform-web/src/main/java/com/flink/platform/web/lifecycle/WorkerHeartbeat.java \
  flink-platform-web/src/test/java/com/flink/platform/web/runner \
  flink-platform-web/src/test/java/com/flink/platform/web/lifecycle/WorkerHeartbeatTest.java
git commit -m "refactor(web): fence flow-run execution ownership"
```

---

### Task 6: Flow-Run Kill Intent and Reconciliation

**Files:**
- Create: `flink-platform-web/src/main/java/com/flink/platform/web/flowrun/FlowRunKillService.java`
- Create: `flink-platform-web/src/test/java/com/flink/platform/web/flowrun/FlowRunKillServiceTest.java`
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/service/KillJobService.java`
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/controller/JobFlowRunController.java`
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/runner/FlowRunDispatcher.java`
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/command/subflow/FlowCommandExecutor.java`

**Interfaces:**
- Consumes: `requestKill` and `confirmKilled`.
- Produces: `requestKill(long): FlowRunTransition` and `reconcileKilling(long): boolean`.
- Leaves `KillJobService` responsible only for individual `JobRunInfo` termination.

- [ ] **Step 1: Write failing kill-race and idempotency tests**

```java
@Test
void runningKillPersistsIntentBeforeChildCleanup() {
    when(stateService.requestKill(RUN_ID)).thenReturn(applied(KILLING));
    killService.requestKill(RUN_ID);
    var order = inOrder(stateService, jobKillService);
    order.verify(stateService).requestKill(RUN_ID);
    order.verify(jobKillService).attemptToKillJob(any());
}

@Test
void alreadyTerminalSuccessIsNotRewritten() {
    when(stateService.requestKill(RUN_ID)).thenReturn(rejected(SUCCESS));
    assertEquals(SUCCESS, killService.requestKill(RUN_ID).status());
    verifyNoInteractions(jobKillService);
}
```

- [ ] **Step 2: Implement parent kill orchestration without premature finalization**

For `CREATED/WAITING/SUBMITTED`, return the direct `KILLED` transition. For `RUNNING`, persist `KILLING`, then make a best-effort pass over visible non-terminal children. `advanceKilling` must leave the parent in `KILLING` until Task 7 can prove launch-gate closure.

- [ ] **Step 3: Migrate callers**

Inject `FlowRunKillService` into `JobFlowRunController`, `FlowRunCoordinator`, and `FlowCommandExecutor`. Remove the controller's read-then-write status check; map the transition returned by `requestKill` to the existing response codes. Remove `killFlowRun` and `forceKillFlowRun` from `KillJobService`; keep `killJob` and `attemptToKillJob` there.

- [ ] **Step 4: Run tests and commit**

```bash
./mvnw -pl flink-platform-web -am \
  -Dtest=FlowRunKillServiceTest,FlowRunDispatcherTest,FlowCommandExecutorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw spotless:apply
git add flink-platform-web/src/main/java/com/flink/platform/web/flowrun/FlowRunKillService.java \
  flink-platform-web/src/main/java/com/flink/platform/web/service/KillJobService.java \
  flink-platform-web/src/main/java/com/flink/platform/web/controller/JobFlowRunController.java \
  flink-platform-web/src/main/java/com/flink/platform/web/runner/FlowRunDispatcher.java \
  flink-platform-web/src/main/java/com/flink/platform/web/command/subflow/FlowCommandExecutor.java \
  flink-platform-web/src/test/java/com/flink/platform/web/flowrun/FlowRunKillServiceTest.java
git commit -m "refactor(web): separate flow-run kill orchestration"
```

---

### Task 7: Minimal Job-Run Launch/Kill Fence

**Files:**
- Modify: `flink-platform-dao/src/main/java/com/flink/platform/dao/service/JobRunInfoService.java`
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/service/JobRunExtraService.java`
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/service/ProcessJobService.java`
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/service/KillJobService.java`
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/runner/JobExecuteThread.java`
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/flowrun/FlowRunKillService.java`
- Create: `flink-platform-web/src/test/java/com/flink/platform/web/service/JobRunLaunchFenceTest.java`

**Interfaces:**
- Produces: `JobRunInfoService.updateStatusFromTo(Long, ExecutionStatus, ExecutionStatus): boolean` and `updateStatusFromAnyTo(Long, Collection<ExecutionStatus>, ExecutionStatus, LocalDateTime): int` for the limited launch/kill edges.
- Produces: `JobRunExtraService.reserveJobRun(JobInfo, long, String): @Nullable Long`.
- Completes: `FlowRunKillService.reconcileKilling` finalizes only when all admitted children are terminal.

- [ ] **Step 1: Write failing parent-reservation gate tests**

```java
@Test
void reservationIsRejectedAfterParentKilling() {
    when(flowRunService.findStateForUpdate(FLOW_RUN_ID))
            .thenReturn(flowState(KILLING, HOST_IP));
    assertNull(extraService.reserveJobRun(jobInfo, FLOW_RUN_ID, HOST_IP));
    verify(jobRunService, never()).save(any());
}

@Test
void reservationPersistsChildWhileParentIsRunningAndOwnedLocally() {
    when(flowRunService.findStateForUpdate(FLOW_RUN_ID))
            .thenReturn(flowState(RUNNING, HOST_IP));
    assertNotNull(extraService.reserveJobRun(jobInfo, FLOW_RUN_ID, HOST_IP));
}
```

Implement `reserveJobRun` as a short `@Transactional` method: lock the parent, verify `RUNNING + expectedHost`, choose the worker, and insert the `CREATED` child before commit.

- [ ] **Step 2: Write failing external-launch claim tests**

```java
@Test
void onlyCreatedToSubmittedWinnerStartsExternalCommand() throws Exception {
    when(jobRunService.updateStatusFromTo(JOB_RUN_ID, CREATED, SUBMITTED)).thenReturn(false);
    when(jobRunService.getById(JOB_RUN_ID)).thenReturn(jobRun(KILLING));
    processJobService.processJob(JOB_RUN_ID);
    verifyNoInteractions(commandExecutors);
}
```

- [ ] **Step 3: Add the minimal job status CAS contract**

Before external command execution, claim `CREATED → SUBMITTED`. Job kill performs direct `CREATED → KILLED`, or `SUBMITTED/RUNNING → KILLING`, then `KILLING → KILLED` only after termination succeeds. Process completion updates only from `SUBMITTED/RUNNING`, so it cannot overwrite `KILLING`.

If a duplicate process request observes `SUBMITTED/RUNNING`, it returns the existing job id without launching another command. The existing monitor/recovery path remains responsible for observing that job; do not add retry scopes or attempt columns.

Add tests for both commit orders of `CREATED → SUBMITTED` versus `CREATED → KILLED`, for a completion callback rejected by `KILLING`, and for a failed external kill leaving the child durably at `KILLING` for reconciliation.

- [ ] **Step 4: Finish parent reconciliation**

`FlowRunKillService.reconcileKilling` reloads all children. It calls `confirmKilled` only when every child is terminal and no child remains `CREATED/SUBMITTED/RUNNING/KILLING`; otherwise it retains parent `KILLING` for the next dispatcher cycle.

- [ ] **Step 5: Run tests and commit**

```bash
./mvnw -pl flink-platform-web -am \
  -Dtest=JobRunLaunchFenceTest,FlowRunKillServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw spotless:apply
git add flink-platform-dao/src/main/java/com/flink/platform/dao/service/JobRunInfoService.java \
  flink-platform-web/src/main/java/com/flink/platform/web/service/JobRunExtraService.java \
  flink-platform-web/src/main/java/com/flink/platform/web/service/ProcessJobService.java \
  flink-platform-web/src/main/java/com/flink/platform/web/service/KillJobService.java \
  flink-platform-web/src/main/java/com/flink/platform/web/runner/JobExecuteThread.java \
  flink-platform-web/src/main/java/com/flink/platform/web/flowrun/FlowRunKillService.java \
  flink-platform-web/src/test/java/com/flink/platform/web/service/JobRunLaunchFenceTest.java
git commit -m "feat(web): fence job launch against flow kill"
```

---

### Task 8: API Compatibility, `JOB_LIST` Isolation, and Activation Flag

**Files:**
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/config/WorkerConfig.java`
- Modify: `flink-platform-web/src/main/resources/application.yml`
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/flowrun/FlowRunStateService.java`
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/controller/JobFlowRunController.java`
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/dto/request/JobFlowRunRequest.java`
- Modify: `flink-platform-ui/frontend/src/api/job-flow.js`
- Modify: `flink-platform-ui/frontend/src/views/project/instances.vue`
- Create: `flink-platform-web/src/test/java/com/flink/platform/web/flowrun/FlowRunLifecycleGuardTest.java`
- Modify: `flink-platform-web/src/test/java/com/flink/platform/web/config/WorkerConfigTest.java`

**Interfaces:**
- Produces: `worker.flow-run-lifecycle-enabled`, default `false`.
- Produces: explicit audited force-resolve endpoint while preserving legacy `{id,status:KILLED}` for one release.
- Preserves: `JOB_LIST` legacy execution and status writes.

- [ ] **Step 1: Write failing configuration and bridge-mode tests**

```java
@Test
void lifecycleFlagDefaultsToFalse() {
    assertFalse(bindWorkerConfig(Map.of()).isFlowRunLifecycleEnabled());
}

@Test
void disabledFacadeUsesCompatibilityWrite() {
    when(workerConfig.isFlowRunLifecycleEnabled()).thenReturn(false);
    stateService.completeExecution(1L, HOST_IP, SUCCESS);
    verify(flowRunService).updateLegacyStatusById(eq(1L), eq(SUCCESS), any());
}
```

Keep the flag branch inside `FlowRunStateService`; callers never branch on configuration. When enabled, use CAS. When disabled, delegate to preserved legacy persistence helpers.

Update every `FlowRunStateService`, admission, execution, and kill unit-test fixture to return `true` from `workerConfig.isFlowRunLifecycleEnabled()` unless the test explicitly covers bridge-disabled behavior.

- [ ] **Step 2: Add explicit force-resolve and legacy adapter tests**

```java
@Test
void legacyKilledUpdateDelegatesToAdministrativeConfirmation() {
    controller.update(request(7L, KILLED));
    verify(stateService).administrativelyConfirmKilled(7L);
}

@Test
void genericUpdateRejectsOtherLifecycleFields() {
    assertTrue(request(7L, SUCCESS).validateOnUpdate().contains("status"));
}
```

Add `POST /jobFlowRun/force-killed/{flowRunId}` as the explicit audited administrative confirmation operation. Add `forceKillFlowRun(flowRunId)` to `frontend/src/api/job-flow.js` and update the UI “Set KILLED” action to use it. Keep only the exact old `{id,status:KILLED}` request as a compatibility adapter; terminal winners remain unchanged.

- [ ] **Step 3: Add the source-level lifecycle write guard**

`FlowRunLifecycleGuardTest` walks `src/main/java` and fails when production code outside these allow-listed files performs direct flow-run status persistence:

```java
private static final Set<String> ALLOWED = Set.of(
        "com/flink/platform/dao/service/JobFlowRunService.java",
        "com/flink/platform/web/quartz/JobFlowRunner.java"); // JOB_LIST legacy only
```

Match `jobFlowRunService.updateStatusById`, `jobFlowRunService.updateById` paired with `setStatus`, and `saveOrUpdate` of a status-bearing `JobFlowRun`. Add a comment to the `JobFlowRunner` allow-list entry referencing `JOB_LIST` production migration.

In the same guard test, read every source file under `web/flowrun` and fail if it imports `JobFlowType` or contains `JOB_LIST`; type routing belongs only at the caller boundary.

- [ ] **Step 4: Run backend and frontend checks**

```bash
./mvnw -pl flink-platform-web -am \
  -Dtest=WorkerConfigTest,FlowRunLifecycleGuardTest,JobFlowRunnerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
npm --prefix flink-platform-ui/frontend run lint
./mvnw spotless:apply
```

- [ ] **Step 5: Commit**

```bash
git add flink-platform-web/src/main/java/com/flink/platform/web/config/WorkerConfig.java \
  flink-platform-web/src/main/resources/application.yml \
  flink-platform-web/src/main/java/com/flink/platform/web/flowrun/FlowRunStateService.java \
  flink-platform-web/src/main/java/com/flink/platform/web/controller/JobFlowRunController.java \
  flink-platform-web/src/main/java/com/flink/platform/web/dto/request/JobFlowRunRequest.java \
  flink-platform-ui/frontend/src/api/job-flow.js \
  flink-platform-ui/frontend/src/views/project/instances.vue \
  flink-platform-web/src/test/java/com/flink/platform/web/flowrun/FlowRunLifecycleGuardTest.java \
  flink-platform-web/src/test/java/com/flink/platform/web/config/WorkerConfigTest.java
git commit -m "feat(web): add flow-run lifecycle compatibility gate"
```

---

### Task 9: Observability, Release Procedure, and Full Verification

**Files:**
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/flowrun/FlowRunStateService.java`
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/flowrun/FlowRunKillService.java`
- Create: `docs/flow-run-lifecycle-rollout.md`
- Create: `flink-platform-web/src/test/java/com/flink/platform/web/flowrun/FlowRunMetricsTest.java`

**Interfaces:**
- Produces: structured transition logs and Micrometer counters by operation/outcome/status.
- Produces: bridge rollout, activation, rollback, and `JOB_LIST` exception procedure.

- [ ] **Step 1: Write failing metric ownership tests**

```java
@Test
void rejectedContentionIsCountedWithoutSystemFailure() {
    when(flowRunService.updateStatusFromAnyTo(anyLong(), any(), any(), any(), any())).thenReturn(0);
    when(flowRunService.findState(1L)).thenReturn(state(KILLING));
    stateService.completeExecution(1L, HOST_IP, SUCCESS);
    assertEquals(1.0, registry.counter(
            "flow_run_transition", "operation", "complete", "outcome", "rejected").count());
}
```

- [ ] **Step 2: Add structured logging and bounded counters**

Log `flowRunId, operation, expectedStatuses, expectedHost, targetStatus, outcome, status, worker`. Count applied/rejected/not-found transitions, exhausted transaction retries, and repeated `KILLING` cleanup failures. Do not label metrics by flow-run id.

- [ ] **Step 3: Write the rollout runbook**

Document these exact stages:

1. deploy the bridge build with `worker.flow-run-lifecycle-enabled=false`;
2. verify every worker reports the new build and false flag;
3. briefly pause lifecycle mutations;
4. enable the flag cluster-wide and verify it on every worker;
5. resume mutations and watch rejection/not-found/cleanup counters;
6. roll back by disabling the flag, never by downgrading only part of the cluster;
7. keep `JOB_LIST` on the documented legacy route until its separate data migration.

- [ ] **Step 4: Run the complete relevant verification suite**

```bash
./mvnw -pl flink-platform-web -am test
./mvnw -pl flink-platform-web -am -Dtest=FlowRunCasConcurrencyTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw spotless:check
git diff --check
```

Expected: all tests PASS, Testcontainers cases pass for MySQL and PostgreSQL, formatting and diff checks produce no errors.

- [ ] **Step 5: Commit the operational slice**

```bash
git add flink-platform-web/src/main/java/com/flink/platform/web/flowrun \
  flink-platform-web/src/test/java/com/flink/platform/web/flowrun/FlowRunMetricsTest.java \
  docs/flow-run-lifecycle-rollout.md
git commit -m "feat(web): operationalize flow-run lifecycle"
```

---

## Final Review Checklist

- [ ] Every non-`JOB_LIST` production flow-run status write reaches `FlowRunStateService`.
- [ ] `JOB_LIST` behavior and production data remain unchanged and explicitly allow-listed.
- [ ] `FlowRunStateService` contains no `JobFlowType` branch.
- [ ] Terminal states and `KILLING` cannot be overwritten by normal completion.
- [ ] Old hosts cannot complete reassigned runs.
- [ ] Pre-created activation cannot overwrite a concurrent kill.
- [ ] Child reservation and launch are fenced before parent kill finalization.
- [ ] Only applied terminal transitions send one-shot alerts.
- [ ] MySQL and PostgreSQL concurrency tests report identical domain outcomes.
- [ ] Bridge-disabled mode preserves legacy behavior; bridge-enabled mode uses CAS.
- [ ] REST response shapes remain compatible and the old Set-KILLED request delegates safely.
- [ ] Full backend tests, frontend lint, Spotless, and `git diff --check` pass.
