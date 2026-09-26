package com.flink.platform.web.service;

import com.flink.platform.common.enums.ExecutionCondition;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.Dependency;
import com.flink.platform.dao.entity.DependencyConfig;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.dao.view.JobFlowLatestRun;
import com.flink.platform.web.quartz.QuartzService;
import com.flink.platform.web.service.DependencyService.DependencyState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.enums.ExecutionCondition.ALL_DONE;
import static com.flink.platform.common.enums.ExecutionCondition.ALL_MATCHED;
import static com.flink.platform.common.enums.ExecutionCondition.ANY_DONE;
import static com.flink.platform.common.enums.ExecutionCondition.ANY_MATCHED;
import static com.flink.platform.common.enums.ExecutionStatus.EXPECTED_FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static com.flink.platform.common.enums.ExpiryPolicy.FIXED_DURATION;
import static com.flink.platform.common.enums.ExpiryPolicy.NEVER;
import static com.flink.platform.common.enums.ExpiryPolicy.UPSTREAM_SCHEDULE;
import static com.flink.platform.common.enums.UnmatchedPolicy.FAIL;
import static com.flink.platform.common.enums.UnmatchedPolicy.WAIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Verifies the four-valued, four-condition prerequisite-dependency evaluation of {@link DependencyService}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DependencyServiceTest {

    @Mock
    private JobFlowService jobFlowService;

    private DependencyService dependencyService;

    /** Stand-in for the rows the service prefetches, keyed the way the batch lookup keys them. */
    private final Map<Long, JobFlowLatestRun> flowRunsByFlowId = new HashMap<>();

    @BeforeEach
    void setUp() {
        // Real QuartzService, not a mock: nextTriggerTimeAfter is pure cron arithmetic and never touches the
        // injected scheduler, and mocking it would leave the UPSTREAM_SCHEDULE cases asserting nothing.
        dependencyService = new DependencyService(jobFlowService, new QuartzService());
        when(jobFlowService.mapLatestRunByFlowId(any())).thenAnswer(call -> subsetOf(flowRunsByFlowId, call));
    }

    /** Both phases, in the order production runs them. */
    private DependencyState stateOf(DependencyConfig config) {
        dependencyService.populateLatestExecutions(List.of(config));
        return dependencyService.evaluate(config);
    }

    private <T> Map<Long, T> subsetOf(Map<Long, T> table, InvocationOnMock call) {
        Collection<Long> ids = call.getArgument(0);
        var found = new HashMap<Long, T>();
        ids.stream().filter(table::containsKey).forEach(id -> found.put(id, table.get(id)));
        return found;
    }

    // --- one item ---

    /** A run only reaches WAITING when it has dependencies, so evaluate never sees a null config. */
    @Test
    void emptyConfigIsSatisfied() {
        assertEquals(DependencyState.SATISFIED, stateOf(new DependencyConfig()));
    }

    @Test
    void upstreamInExpectedStatusAndFreshIsSatisfied() {
        upstreamFlow(100L, SUCCESS, minutesAgo(5));
        assertEquals(DependencyState.SATISFIED, stateOf(config(ALL_MATCHED, item(100L, List.of(SUCCESS)))));
    }

    @Test
    void oneOffEvaluationResolvesLatestExecutionBeforeEvaluating() {
        upstreamFlow(100L, SUCCESS, minutesAgo(5));
        var config = config(ALL_MATCHED, item(100L, List.of(SUCCESS)));

        assertEquals(DependencyState.SATISFIED, dependencyService.resolveAndEvaluate(config));
    }

    @Test
    void upstreamStillRunningIsPending() {
        upstreamFlow(100L, RUNNING, minutesAgo(5));
        assertEquals(DependencyState.PENDING, stateOf(config(ALL_MATCHED, item(100L, List.of(SUCCESS)))));
    }

    @Test
    void upstreamWaitingOnItsOwnDependenciesIsPending() {
        upstreamFlow(100L, WAITING, minutesAgo(5));
        assertEquals(DependencyState.PENDING, stateOf(config(ALL_MATCHED, item(100L, List.of(SUCCESS)))));
    }

    @Test
    void upstreamNeverRanIsPending() {
        upstreamFlowWithoutRun(100L, null);
        assertEquals(DependencyState.PENDING, stateOf(config(ALL_MATCHED, item(100L, List.of(SUCCESS)))));
    }

    /** The cron is read from the flow itself, so it is there to judge the expiry by before the first run. */
    @Test
    void upstreamThatNeverRanIsPendingEvenUnderUpstreamSchedule() {
        upstreamFlowWithoutRun(100L, "0 0 3 * * ?");
        var item = item(100L, List.of(SUCCESS));
        item.setExpiry(UPSTREAM_SCHEDULE);
        item.setDuration(null);
        assertEquals(DependencyState.PENDING, stateOf(config(ALL_MATCHED, item)));
    }

    /** An upstream deleted since the dependency was written leaves no row at all, not even a cron. */
    @Test
    void upstreamThatNoLongerExistsIsPending() {
        assertEquals(DependencyState.PENDING, stateOf(config(ALL_MATCHED, item(100L, List.of(SUCCESS)))));
    }

    /** A null expiry is unusable too, and must not escape as an exception the dispatcher swallows. */
    @Test
    void nullExpiryIsMisconfigured() {
        upstreamFlow(100L, SUCCESS, minutesAgo(5));
        var item = item(100L, List.of(SUCCESS));
        item.setExpiry(null);
        item.setDuration(null);
        assertEquals(DependencyState.MISCONFIGURED, stateOf(config(ALL_MATCHED, item)));
    }

    /**
     * A broken dependency must not wait silently until its first upstream run turns it into an alert. Nothing
     * here needs a run to be judged: the cron comes from the flow, the rest from the dependency itself.
     */
    @Test
    void configFaultsAreMisconfiguredBeforeTheUpstreamEverRuns() {
        upstreamFlowWithoutRun(100L, "0 0 3 * * ?");

        var noExpiry = item(100L, List.of(SUCCESS));
        noExpiry.setExpiry(null);
        noExpiry.setDuration(null);
        assertEquals(DependencyState.MISCONFIGURED, stateOf(config(ALL_MATCHED, noExpiry)));

        var noStatuses = item(100L, List.of());
        assertEquals(DependencyState.MISCONFIGURED, stateOf(config(ALL_MATCHED, noStatuses)));

        var badDuration = item(100L, List.of(SUCCESS));
        badDuration.setDuration("every now and then");
        assertEquals(DependencyState.MISCONFIGURED, stateOf(config(ALL_MATCHED, badDuration)));

        var noUpstreamCron = item(100L, List.of(SUCCESS));
        noUpstreamCron.setExpiry(UPSTREAM_SCHEDULE);
        noUpstreamCron.setDuration(null);
        upstreamFlowWithoutRun(100L, null);
        assertEquals(DependencyState.MISCONFIGURED, stateOf(config(ALL_MATCHED, noUpstreamCron)));
    }

    @Test
    void upstreamSettledOnWrongStatusKeepsWaitingByDefault() {
        // The latest run says nothing about the next one: a failed upstream is often re-run by hand.
        upstreamFlow(100L, FAILURE, minutesAgo(5));
        assertEquals(DependencyState.PENDING, stateOf(config(ALL_MATCHED, item(100L, List.of(SUCCESS)))));
    }

    @Test
    void upstreamSettledOnWrongStatusGivesUpUnderFailPolicy() {
        upstreamFlow(100L, FAILURE, minutesAgo(5));
        assertEquals(
                DependencyState.UNSATISFIABLE, stateOf(failFast(config(ALL_MATCHED, item(100L, List.of(SUCCESS))))));
    }

    @Test
    void staleButMatchingUpstreamIsPending() {
        // Status matches, yet the run predates the window: a newer run is expected, so keep waiting.
        upstreamFlow(100L, SUCCESS, LocalDateTime.now().minusHours(5));
        assertEquals(DependencyState.PENDING, stateOf(config(ALL_MATCHED, item(100L, List.of(SUCCESS)))));
    }

    @Test
    void multipleAcceptedStatusesMatchEitherWay() {
        upstreamFlow(100L, EXPECTED_FAILURE, minutesAgo(5));
        assertEquals(
                DependencyState.SATISFIED,
                stateOf(config(ALL_MATCHED, item(100L, List.of(SUCCESS, EXPECTED_FAILURE)))));
    }

    /** An item naming no upstream can never be satisfied by one, so it is broken config, not a free pass. */
    @Test
    void itemWithoutFlowIdIsMisconfigured() {
        assertEquals(DependencyState.MISCONFIGURED, stateOf(config(ALL_MATCHED, item(null, List.of(SUCCESS)))));
    }

    // --- UPSTREAM_SCHEDULE: window comes from the upstream's own cron ---

    @Test
    void asExpectedIsSatisfiedInsideUpstreamCronWindow() {
        upstreamFlow(100L, SUCCESS, minutesAgo(5)).setFlowCronExpr("0 0 3 * * ?"); // daily at 03:00
        var item = item(100L, List.of(SUCCESS));
        item.setExpiry(UPSTREAM_SCHEDULE);
        item.setDuration(null);
        assertEquals(DependencyState.SATISFIED, stateOf(config(ALL_MATCHED, item)));
    }

    @Test
    void asExpectedIsPendingOnceUpstreamCronHasFiredAgain() {
        upstreamFlow(100L, SUCCESS, LocalDateTime.now().minusDays(3)).setFlowCronExpr("0 0 3 * * ?");
        var item = item(100L, List.of(SUCCESS));
        item.setExpiry(UPSTREAM_SCHEDULE);
        item.setDuration(null);
        assertEquals(DependencyState.PENDING, stateOf(config(ALL_MATCHED, item)));
    }

    /** The cron is read from the flow definition, so dropping the schedule leaves the item with no window. */
    @Test
    void asExpectedIsMisconfiguredWhenUpstreamHasNoCron() {
        upstreamFlow(100L, SUCCESS, minutesAgo(5)).setFlowCronExpr(null);
        var item = item(100L, List.of(SUCCESS));
        item.setExpiry(UPSTREAM_SCHEDULE);
        item.setDuration(null);
        assertEquals(DependencyState.MISCONFIGURED, stateOf(config(ALL_MATCHED, item)));
    }

    /** A present but unparsable cron is just as unusable; asking Quartz for the next fire would throw. */
    @Test
    void asExpectedIsMisconfiguredWhenUpstreamCronIsInvalid() {
        upstreamFlow(100L, SUCCESS, minutesAgo(5)).setFlowCronExpr("not-a-valid-cron");
        var item = item(100L, List.of(SUCCESS));
        item.setExpiry(UPSTREAM_SCHEDULE);
        item.setDuration(null);
        assertEquals(DependencyState.MISCONFIGURED, stateOf(config(ALL_MATCHED, item)));
    }

    // --- FIXED_DURATION: window comes from the dependency's own duration ---

    /** Without a duration there is no window to measure the run against, so the expiry is unusable. */
    @Test
    void fixedDurationWithoutDurationIsMisconfigured() {
        upstreamFlow(100L, SUCCESS, minutesAgo(5));
        var item = item(100L, List.of(SUCCESS));
        item.setDuration(null);
        assertEquals(DependencyState.MISCONFIGURED, stateOf(config(ALL_MATCHED, item)));
    }

    /** An unparsable duration is the same broken configuration, not an exception for the dispatcher to swallow. */
    @Test
    void fixedDurationWithUnparsableDurationIsMisconfigured() {
        upstreamFlow(100L, SUCCESS, minutesAgo(5));
        var item = item(100L, List.of(SUCCESS));
        item.setDuration("every now and then");
        assertEquals(DependencyState.MISCONFIGURED, stateOf(config(ALL_MATCHED, item)));
    }

    /** A broken item is a configuration problem, so a matching sibling must not mask it under ANY_MATCHED. */
    @Test
    void misconfiguredIsNotMaskedByAMatchingSibling() {
        upstreamFlow(100L, SUCCESS, minutesAgo(5));
        upstreamFlow(200L, SUCCESS, minutesAgo(5)).setFlowCronExpr(null);
        var broken = item(200L, List.of(SUCCESS));
        broken.setExpiry(UPSTREAM_SCHEDULE);
        broken.setDuration(null);
        assertEquals(DependencyState.MISCONFIGURED, stateOf(config(ANY_MATCHED, item(100L, List.of(SUCCESS)), broken)));
    }

    // --- NEVER: no window at all ---

    // --- streaming upstreams: RUNNING is the answer, not a wait ---

    /** A streaming upstream stays RUNNING by design; listing it in statuses is how one depends on a stream. */
    @Test
    void anExplicitlyAcceptedRunningUpstreamIsSatisfied() {
        upstreamFlow(100L, RUNNING, LocalDateTime.now().minusDays(30));
        var item = item(100L, List.of(RUNNING));
        item.setExpiry(NEVER);
        item.setDuration(null);
        assertEquals(DependencyState.SATISFIED, stateOf(config(ALL_MATCHED, item)));
    }

    /**
     * Expiry applies uniformly, in flight or not, so a stream must be depended on with NEVER. A long-lived stream's
     * createTime is its start, which any age-based window would have expired long ago.
     */
    @Test
    void aRunningUpstreamStillHonoursExpiry() {
        upstreamFlow(100L, RUNNING, LocalDateTime.now().minusDays(30));
        var item = item(100L, List.of(RUNNING)); // FIXED_DURATION "1h" from the helper
        assertEquals(DependencyState.PENDING, stateOf(config(ALL_MATCHED, item)));
    }

    /** The stream died: FAILURE is terminal and unaccepted, so the usual unmatched handling applies. */
    @Test
    void aStreamingUpstreamThatDiedIsUnmatched() {
        upstreamFlow(100L, FAILURE, minutesAgo(5));
        var item = item(100L, List.of(RUNNING));
        item.setExpiry(NEVER);
        item.setDuration(null);
        assertEquals(DependencyState.UNSATISFIABLE, stateOf(failFast(config(ALL_MATCHED, item))));
    }

    /** Without RUNNING in statuses the old behaviour holds: in-flight is a wait, never an early failure. */
    @Test
    void anUnacceptedRunningUpstreamNeverFailsFast() {
        upstreamFlow(100L, RUNNING, minutesAgo(5));
        assertEquals(DependencyState.PENDING, stateOf(failFast(config(ALL_MATCHED, item(100L, List.of(SUCCESS))))));
    }

    /** *_DONE asks whether the upstream finished, so an in-flight upstream must not count as done. */
    @Test
    void doneConditionsStillWaitForAnInFlightUpstream() {
        upstreamFlow(100L, RUNNING, minutesAgo(5));
        var item = item(100L, List.of());
        assertEquals(DependencyState.PENDING, stateOf(config(ALL_DONE, item)));
    }

    /**
     * *_DONE asks "did it finish", *_MATCHED asks "did it finish the way I wanted" — two independent questions.
     * Accepting RUNNING answers the second, and must not be mistaken for an answer to the first.
     */
    @Test
    void doneConditionsIgnoreAnAcceptedButUnfinishedStatus() {
        upstreamFlow(100L, RUNNING, minutesAgo(5));
        var item = item(100L, List.of(RUNNING));
        item.setExpiry(NEVER);
        item.setDuration(null);
        assertEquals(DependencyState.PENDING, stateOf(config(ALL_DONE, item)));
        assertEquals(DependencyState.PENDING, stateOf(config(ANY_DONE, item)));
        // The same item under *_MATCHED is satisfied: that question it does answer.
        assertEquals(DependencyState.SATISFIED, stateOf(config(ALL_MATCHED, item)));
    }

    @Test
    void neverAcceptsAnArbitrarilyOldRun() {
        upstreamFlow(100L, SUCCESS, LocalDateTime.now().minusDays(90));
        var item = item(100L, List.of(SUCCESS));
        item.setExpiry(NEVER);
        item.setDuration(null);
        assertEquals(DependencyState.SATISFIED, stateOf(config(ALL_MATCHED, item)));
    }

    @Test
    void neverStillHonoursTheStatusCheck() {
        upstreamFlow(100L, FAILURE, minutesAgo(5));
        var item = item(100L, List.of(SUCCESS));
        item.setExpiry(NEVER);
        item.setDuration(null);
        assertEquals(DependencyState.PENDING, stateOf(config(ALL_MATCHED, item)));
    }

    /** No cron needed, so a scheduleless upstream is fine here — this is the one-shot upstream case. */
    @Test
    void neverNeedsNoUpstreamCron() {
        upstreamFlow(100L, SUCCESS, LocalDateTime.now().minusDays(90)).setFlowCronExpr(null);
        var item = item(100L, List.of(SUCCESS));
        item.setExpiry(NEVER);
        item.setDuration(null);
        assertEquals(DependencyState.SATISFIED, stateOf(config(ALL_MATCHED, item)));
    }

    // --- ALL_MATCHED ---

    @Test
    void allMatchedNeedsEveryItem() {
        upstreamFlow(100L, SUCCESS, minutesAgo(5));
        upstreamFlow(200L, RUNNING, minutesAgo(5));
        assertEquals(
                DependencyState.PENDING,
                stateOf(config(ALL_MATCHED, item(100L, List.of(SUCCESS)), item(200L, List.of(SUCCESS)))));
    }

    @Test
    void allMatchedFailsFastUnderFailPolicy() {
        // One settled-but-unmatched item settles it even while another is still running.
        upstreamFlow(100L, FAILURE, minutesAgo(5));
        upstreamFlow(200L, RUNNING, minutesAgo(5));
        assertEquals(
                DependencyState.UNSATISFIABLE,
                stateOf(failFast(config(ALL_MATCHED, item(100L, List.of(SUCCESS)), item(200L, List.of(SUCCESS))))));
    }

    @Test
    void allMatchedKeepsWaitingOnAnUndeclaredMismatch() {
        upstreamFlow(100L, FAILURE, minutesAgo(5));
        upstreamFlow(200L, RUNNING, minutesAgo(5));
        assertEquals(
                DependencyState.PENDING,
                stateOf(config(ALL_MATCHED, item(100L, List.of(SUCCESS)), item(200L, List.of(SUCCESS)))));
    }

    @Test
    void allMatchedPassesWhenEveryItemMatches() {
        upstreamFlow(100L, SUCCESS, minutesAgo(5));
        upstreamFlow(200L, SUCCESS, minutesAgo(5));
        assertEquals(
                DependencyState.SATISFIED,
                stateOf(config(ALL_MATCHED, item(100L, List.of(SUCCESS)), item(200L, List.of(SUCCESS)))));
    }

    // --- ANY_MATCHED ---

    @Test
    void anyMatchedPassesOnASingleMatch() {
        upstreamFlow(100L, SUCCESS, minutesAgo(5));
        upstreamFlow(200L, FAILURE, minutesAgo(5));
        assertEquals(
                DependencyState.SATISFIED,
                stateOf(config(ANY_MATCHED, item(100L, List.of(SUCCESS)), item(200L, List.of(SUCCESS)))));
    }

    @Test
    void anyMatchedKeepsWaitingWhileAnyItemMightStillCome() {
        upstreamFlow(100L, FAILURE, minutesAgo(5));
        upstreamFlow(200L, RUNNING, minutesAgo(5));
        assertEquals(
                DependencyState.PENDING,
                stateOf(config(ANY_MATCHED, item(100L, List.of(SUCCESS)), item(200L, List.of(SUCCESS)))));
    }

    @Test
    void anyMatchedGivesUpOnlyWhenNoItemCanStillComeThrough() {
        upstreamFlow(100L, FAILURE, minutesAgo(5));
        upstreamFlow(200L, FAILURE, minutesAgo(5));
        assertEquals(
                DependencyState.UNSATISFIABLE,
                stateOf(failFast(config(ANY_MATCHED, item(100L, List.of(SUCCESS)), item(200L, List.of(SUCCESS))))));
    }

    @Test
    void anyMatchedKeepsWaitingWhileOneItemIsUnfinished() {
        // Under FAIL, a still-running item keeps the run alive even though the other already fell short.
        upstreamFlow(100L, FAILURE, minutesAgo(5));
        upstreamFlow(200L, RUNNING, minutesAgo(5));
        assertEquals(
                DependencyState.PENDING,
                stateOf(failFast(config(ANY_MATCHED, item(100L, List.of(SUCCESS)), item(200L, List.of(SUCCESS))))));
    }

    // --- ALL_DONE / ANY_DONE: status is irrelevant, only "has it finished" ---

    @Test
    void allDoneIsSatisfiedByFailedUpstreams() {
        upstreamFlow(100L, FAILURE, minutesAgo(5));
        upstreamFlow(200L, SUCCESS, minutesAgo(5));
        assertEquals(
                DependencyState.SATISFIED,
                stateOf(config(ALL_DONE, item(100L, List.of(SUCCESS)), item(200L, List.of(SUCCESS)))));
    }

    @Test
    void allDoneWaitsForTheUnfinishedOne() {
        upstreamFlow(100L, FAILURE, minutesAgo(5));
        upstreamFlow(200L, RUNNING, minutesAgo(5));
        assertEquals(
                DependencyState.PENDING,
                stateOf(config(ALL_DONE, item(100L, List.of(SUCCESS)), item(200L, List.of(SUCCESS)))));
    }

    @Test
    void anyDoneIsSatisfiedBySingleFinishedUpstream() {
        upstreamFlow(100L, FAILURE, minutesAgo(5));
        upstreamFlow(200L, RUNNING, minutesAgo(5));
        assertEquals(
                DependencyState.SATISFIED,
                stateOf(config(ANY_DONE, item(100L, List.of(SUCCESS)), item(200L, List.of(SUCCESS)))));
    }

    @Test
    void anyDoneWaitsWhileNothingHasFinished() {
        upstreamFlow(100L, RUNNING, minutesAgo(5));
        upstreamFlow(200L, RUNNING, minutesAgo(5));
        assertEquals(
                DependencyState.PENDING,
                stateOf(config(ANY_DONE, item(100L, List.of(SUCCESS)), item(200L, List.of(SUCCESS)))));
    }

    @Test
    void doneConditionsNeedNoStatuses() {
        upstreamFlow(100L, FAILURE, minutesAgo(5));
        assertEquals(DependencyState.SATISFIED, stateOf(config(ALL_DONE, item(100L, null))));
    }

    @Test
    void doneConditionsIgnoreTheUnmatchedPolicy() {
        // Under *_DONE only "has it finished" matters, so even FAIL can't turn a finished upstream into a give-up.
        upstreamFlow(100L, FAILURE, minutesAgo(5));
        assertEquals(DependencyState.SATISFIED, stateOf(failFast(config(ALL_DONE, item(100L, List.of(SUCCESS))))));
    }

    @Test
    void doneConditionsAreNeverUnsatisfiable() {
        // Even an all-failed upstream set only ever yields SATISFIED or PENDING under *_DONE.
        upstreamFlow(100L, FAILURE, minutesAgo(5));
        assertEquals(DependencyState.SATISFIED, stateOf(config(ANY_DONE, item(100L, List.of(SUCCESS)))));
    }

    // --- persistence / wire compatibility ---

    @Test
    void legacyAndOrNamesDeserializeIntoPreconditions() {
        // Lets the existing dependent editor, which posts AND/OR, drive flow-level dependencies unchanged.
        assertEquals(
                ALL_MATCHED,
                JsonUtil.toBean("{\"precondition\":\"AND\"}", DependencyConfig.class)
                        .getPrecondition());
        assertEquals(
                ANY_MATCHED,
                JsonUtil.toBean("{\"precondition\":\"OR\"}", DependencyConfig.class)
                        .getPrecondition());
    }

    @Test
    void evaluationScratchStateIsNotPersisted() {
        // The latest* fields are filled in while evaluating; persisting them would bloat the depends_on column
        // and store stale run ids in the flow definition.
        upstreamFlow(100L, SUCCESS, minutesAgo(5));
        var config = config(ALL_MATCHED, item(100L, List.of(SUCCESS)));
        stateOf(config);

        var json = JsonUtil.toJsonString(config);
        assertFalse(json.contains("latest"), json);
    }

    @Test
    void evaluatingWithoutPopulatingFailsLoudly() {
        // Otherwise the run would just sit at PENDING and fail at its deadline blaming the upstream.
        upstreamFlow(100L, SUCCESS, minutesAgo(5));
        var config = config(ALL_MATCHED, item(100L, List.of(SUCCESS)));
        assertThrows(IllegalStateException.class, () -> dependencyService.evaluate(config));
    }

    @Test
    void fieldsDefaultWhenAbsent() {
        // Rows written before the fields existed deserialize into the defaults, not into nulls that would NPE
        // the precondition switch above. dependencies keeps no default: hasDependencies() reads it null-safely,
        // and every other read goes through that guard.
        var config = JsonUtil.toBean("{}", DependencyConfig.class);
        assertNull(config.getDependencies());
        assertFalse(config.hasDependencies());
        assertEquals(ALL_MATCHED, config.getPrecondition());
        assertEquals(WAIT, config.getOnUnmatched());
    }

    @Test
    void fieldsDefaultWhenExplicitlyNull() {
        // A front end that posts an unset select as null must be read the same way as one that omits the key.
        // @NullMarked cannot reach this: Jackson drives the setters by reflection, past any compile-time check.
        var config = JsonUtil.toBean(
                "{\"dependencies\":null,\"precondition\":null,\"onUnmatched\":null}", DependencyConfig.class);
        assertNull(config.getDependencies());
        assertEquals(ALL_MATCHED, config.getPrecondition());
        assertEquals(WAIT, config.getOnUnmatched());
    }

    @Test
    void defaultsSurviveARoundTrip() {
        // Writing a config back out and reading it in must not drift: serialisation is how these reach the
        // depends_on column, so a default that only held on the way in would be lost on the next read.
        var config = JsonUtil.toBean(JsonUtil.toJsonString(new DependencyConfig()), DependencyConfig.class);
        assertNull(config.getDependencies());
        assertEquals(ALL_MATCHED, config.getPrecondition());
        assertEquals(WAIT, config.getOnUnmatched());
    }

    private LocalDateTime minutesAgo(int minutes) {
        return LocalDateTime.now().minusMinutes(minutes);
    }

    /** A flow that exists but has never run: the row still carries its cron, only the latest fields are null. */
    private JobFlowLatestRun upstreamFlowWithoutRun(Long flowId, String cronExpr) {
        var flow = new JobFlowLatestRun();
        flow.setFlowId(flowId);
        flow.setFlowCronExpr(cronExpr);
        flowRunsByFlowId.put(flowId, flow);
        return flow;
    }

    private JobFlowLatestRun upstreamFlow(Long flowId, ExecutionStatus status, LocalDateTime createTime) {
        var flow = new JobFlowLatestRun();
        flow.setFlowId(flowId);
        flow.setLatestRunId(flowId + 1);
        flow.setLatestStatus(status);
        flow.setLatestCreateTime(createTime);
        flowRunsByFlowId.put(flowId, flow);
        return flow;
    }

    private DependencyConfig config(ExecutionCondition precondition, Dependency... items) {
        var cfg = new DependencyConfig();
        cfg.setPrecondition(precondition);
        cfg.setDependencies(Arrays.asList(items));
        return cfg;
    }

    private Dependency item(Long flowId, List<ExecutionStatus> statuses) {
        var item = new Dependency();
        item.setFlowId(flowId);
        item.setStatuses(statuses);
        item.setExpiry(FIXED_DURATION);
        item.setDuration("1h");
        return item;
    }

    private DependencyConfig failFast(DependencyConfig config) {
        config.setOnUnmatched(FAIL);
        return config;
    }
}
