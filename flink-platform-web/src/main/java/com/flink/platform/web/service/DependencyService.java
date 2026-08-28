package com.flink.platform.web.service;

import com.flink.platform.common.enums.ExecutionCondition;
import com.flink.platform.dao.entity.Dependency;
import com.flink.platform.dao.entity.DependencyConfig;
import com.flink.platform.dao.service.JobFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.quartz.CronExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.flink.platform.common.enums.ExpiryPolicy.NEVER;
import static com.flink.platform.common.enums.UnmatchedPolicy.FAIL;
import static com.flink.platform.web.service.DependencyService.DependencyState.MISCONFIGURED;
import static com.flink.platform.web.service.DependencyService.DependencyState.PENDING;
import static com.flink.platform.web.service.DependencyService.DependencyState.SATISFIED;
import static com.flink.platform.web.service.DependencyService.DependencyState.UNSATISFIABLE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Evaluates a job flow's dependencies.
 */
@NullMarked
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DependencyService {

    private final JobFlowService jobFlowService;

    private final QuartzService quartzService;

    public DependencyState resolveAndEvaluate(@Nullable DependencyConfig config) {
        if (config == null || !config.hasDependencies()) {
            return SATISFIED;
        }

        populateLatestExecutions(List.of(config));
        return evaluate(config);
    }

    public void populateLatestExecutions(List<DependencyConfig> configs) {
        configs.forEach(config -> config.setLatestResolved(true));
        var dependencies = configs.stream()
                .filter(DependencyConfig::hasDependencies)
                .flatMap(config -> config.getDependencies().stream())
                .filter(item -> item.getFlowId() != null)
                .toList();
        if (dependencies.isEmpty()) {
            return;
        }

        var flowIds =
                dependencies.stream().map(Dependency::getFlowId).distinct().toList();
        var latestRuns = jobFlowService.mapLatestRunByFlowId(flowIds);

        for (var item : dependencies) {
            var upstream = latestRuns.get(item.getFlowId());
            if (upstream != null) {
                item.setLatestFlowRunId(upstream.getLatestRunId());
                item.setLatestStatus(upstream.getLatestStatus());
                item.setLatestCreateTime(upstream.getLatestCreateTime());
                item.setFlowCronExpr(upstream.getFlowCronExpr());
            }
        }
    }

    /**
     * Call {@link #populateLatestExecutions} first.
     */
    public DependencyState evaluate(@Nullable DependencyConfig config) {
        if (config == null || !config.hasDependencies()) {
            return SATISFIED;
        }

        if (!config.isLatestResolved()) {
            throw new IllegalStateException("Dependencies are unresolved, call populateLatestExecutions first");
        }

        final var precondition = config.getPrecondition();
        var states = config.getDependencies().stream()
                .map(one -> evaluateOne(one, precondition))
                .collect(Collectors.toSet());
        if (states.contains(MISCONFIGURED)) {
            return MISCONFIGURED;
        }

        // ALL_* wants nothing but SATISFIED, ANY_* wants only one.
        if (precondition.requiresAllQualified() ? allSatisfied(states) : states.contains(SATISFIED)) {
            return SATISFIED;
        }

        // Only PENDING and UNSATISFIABLE are left: ALL_* dies on any UNSATISFIABLE, ANY_* lives on any PENDING.
        if (precondition.requiresAllQualified() ? !states.contains(UNSATISFIABLE) : states.contains(PENDING)) {
            return PENDING;
        }

        // Nothing left to wait for, but waiting is still the default: a failed upstream is often re-run by hand.
        // *_DONE never gets here, its items never report UNSATISFIABLE.
        return FAIL == config.getOnUnmatched() ? UNSATISFIABLE : PENDING;
    }

    // ====================================================
    // ====================== private =====================
    // ====================================================

    private static boolean allSatisfied(Set<DependencyState> states) {
        return states.stream().allMatch(state -> state == SATISFIED);
    }

    private DependencyState evaluateOne(Dependency one, ExecutionCondition condition) {
        if (!isWellConfigured(one, condition)) {
            log.warn("Dependency can't be evaluated: precondition={}, dependency={}", condition, one);
            return MISCONFIGURED;
        }

        var latestStatus = one.getLatestStatus();
        if (latestStatus == null) {
            return PENDING;
        }

        if (!isLatestRunWithinWindow(one)) {
            return PENDING;
        }

        // *_MATCHED wants the expected status, *_DONE wants only a terminal one.
        var finished = latestStatus.isTerminalState();
        if (condition.requiresStatusMatched() ? one.getStatuses().contains(latestStatus) : finished) {
            return SATISFIED;
        }

        // A finished run is the upstream's final word, an unfinished one hasn't spoken yet.
        return finished ? UNSATISFIABLE : PENDING;
    }

    private static boolean isWellConfigured(Dependency item, ExecutionCondition condition) {
        if (item.getFlowId() == null) {
            return false;
        }

        if (condition.requiresStatusMatched() && CollectionUtils.isEmpty(item.getStatuses())) {
            return false;
        }

        var expiry = item.getExpiry();
        if (expiry == null) {
            return false;
        }

        return switch (expiry) {
            case UPSTREAM_SCHEDULE ->
                isNotBlank(item.getFlowCronExpr()) && CronExpression.isValidExpression(item.getFlowCronExpr());
            case FIXED_DURATION -> item.hasValidDuration();
            case NEVER -> true;
        };
    }

    private boolean isLatestRunWithinWindow(Dependency item) {
        if (item.getExpiry() == NEVER) {
            return true;
        }

        var createAt = item.getLatestCreateTime();
        if (createAt == null) {
            return false;
        }

        var now = LocalDateTime.now();
        return switch (item.getExpiry()) {
            case UPSTREAM_SCHEDULE ->
                quartzService
                        .nextTriggerTimeAfter(item.getFlowCronExpr(), createAt)
                        .isAfter(now);
            case FIXED_DURATION -> createAt.isAfter(now.minus(item.parseDuration()));
            case NEVER -> throw new IllegalStateException("unreachable");
        };
    }

    /**
     * Where one dependency item, or a whole group, stands. The first three are one axis — satisfied
     * now, maybe later, never; MISCONFIGURED is off it: no answer is possible.
     */
    public enum DependencyState {

        /** Go: submit the run. */
        SATISFIED,

        /** Keep waiting: a later upstream run may still satisfy it. */
        PENDING,

        /**
         * Stop waiting and fail: the upstream's final answer is not one this dependency accepts.
         * Only reported when the group's {@code onUnmatched} says FAIL.
         */
        UNSATISFIABLE,

        /**
         * Fail and alert: no upstream outcome could satisfy this item — no usable expiry window, or
         * no accepted status under {@code *_MATCHED}. Ends the wait whatever {@code onUnmatched}
         * says, and one such item invalidates the whole group.
         */
        MISCONFIGURED
    }
}
