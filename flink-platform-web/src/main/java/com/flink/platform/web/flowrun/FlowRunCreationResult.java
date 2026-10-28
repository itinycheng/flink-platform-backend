package com.flink.platform.web.flowrun;

import com.flink.platform.dao.entity.JobFlowRun;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static com.flink.platform.common.constants.Constant.EMPTY;

public record FlowRunCreationResult(
        State state,
        JobFlowRun run,
        @Nullable JobFlowRun blockOn,
        @Nullable String blockReason,
        List<WaitTimeout> timeouts) {

    public static FlowRunCreationResult created(JobFlowRun run) {
        return created(run, List.of());
    }

    public static FlowRunCreationResult created(JobFlowRun run, List<WaitTimeout> timeouts) {
        return new FlowRunCreationResult(State.CREATED, run, null, null, timeouts);
    }

    public static FlowRunCreationResult blocked(
            JobFlowRun run, JobFlowRun blockOn, String reason, List<WaitTimeout> timeouts) {
        return new FlowRunCreationResult(State.BLOCKED, run, blockOn, reason, timeouts);
    }

    public static FlowRunCreationResult blocked(JobFlowRun run, String reason) {
        return new FlowRunCreationResult(State.BLOCKED, run, null, reason, List.of());
    }

    public String blockMessage() {
        if (StringUtils.isBlank(blockReason)) {
            return EMPTY;
        }
        return blockOn == null ? blockReason : "Flow run rejected: " + blockReason + ", block on: " + blockOn.getId();
    }

    public enum State {
        CREATED,
        BLOCKED
    }

    public record WaitTimeout(JobFlowRun run, String reason) {}
}
