package com.flink.platform.web.model;

import com.flink.platform.dao.entity.JobFlowRun;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record FlowRunCreationResult(
        State state, @Nullable JobFlowRun blockOn, @Nullable String blockReason, List<WaitTimeout> timeouts) {

    public static FlowRunCreationResult created() {
        return created(List.of());
    }

    public static FlowRunCreationResult created(List<WaitTimeout> timeouts) {
        return new FlowRunCreationResult(State.CREATED, null, null, timeouts);
    }

    public static FlowRunCreationResult blocked(JobFlowRun blockOn, String reason, List<WaitTimeout> timeouts) {
        return new FlowRunCreationResult(State.BLOCKED, blockOn, reason, timeouts);
    }

    public enum State {
        CREATED,
        BLOCKED
    }

    public record WaitTimeout(JobFlowRun run, String reason) {}
}
