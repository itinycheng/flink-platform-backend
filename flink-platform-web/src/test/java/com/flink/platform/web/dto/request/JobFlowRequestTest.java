package com.flink.platform.web.dto.request;

import com.flink.platform.common.enums.ExpiryPolicy;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.Dependency;
import com.flink.platform.dao.entity.DependencyConfig;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.flink.platform.common.enums.ExecutionCondition.ALL_DONE;
import static com.flink.platform.common.enums.ExecutionCondition.ALL_MATCHED;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.common.enums.ExpiryPolicy.FIXED_DURATION;
import static com.flink.platform.common.enums.ExpiryPolicy.NEVER;
import static com.flink.platform.common.enums.ExpiryPolicy.UPSTREAM_SCHEDULE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Verifies prerequisite-dependency validation of {@link JobFlowRequest}. */
class JobFlowRequestTest {

    @Test
    void noDependenciesIsValid() {
        assertNull(request(1L).verifyDependsOn());
        assertNull(request(1L, new DependencyConfig()).verifyDependsOn());
    }

    @Test
    void fixedDurationWithDurationIsValid() {
        assertNull(request(1L, config(item(2L, FIXED_DURATION, "1h"))).verifyDependsOn());
    }

    @Test
    void upstreamScheduleWithoutDurationIsValid() {
        assertNull(request(1L, config(item(2L, UPSTREAM_SCHEDULE, null))).verifyDependsOn());
    }

    @Test
    void missingPreconditionFallsBackToAllMatched() {
        // A request that omits the precondition, or posts it as null, gets the default rather than a rejection.
        var cfg = JsonUtil.toBean("{\"precondition\":null}", DependencyConfig.class);
        cfg.setDependencies(List.of(item(2L, FIXED_DURATION, "1h")));
        assertEquals(ALL_MATCHED, cfg.getPrecondition());
        assertNull(request(1L, cfg).verifyDependsOn());
    }

    @Test
    void nullFlowIdIsRejected() {
        assertNotNull(request(1L, config(item(null, FIXED_DURATION, "1h"))).verifyDependsOn());
    }

    @Test
    void selfDependencyIsRejected() {
        assertNotNull(request(1L, config(item(1L, FIXED_DURATION, "1h"))).verifyDependsOn());
    }

    @Test
    void emptyStatusesIsRejectedForMatchedConditions() {
        var item = item(2L, FIXED_DURATION, "1h");
        item.setStatuses(List.of());
        assertNotNull(request(1L, config(item)).verifyDependsOn());
    }

    @Test
    void emptyStatusesIsAllowedForDoneConditions() {
        var item = item(2L, FIXED_DURATION, "1h");
        item.setStatuses(List.of());
        var cfg = config(item);
        cfg.setPrecondition(ALL_DONE);
        assertNull(request(1L, cfg).verifyDependsOn());
    }

    @Test
    void nullExpiryIsRejected() {
        assertNotNull(request(1L, config(item(2L, null, "1h"))).verifyDependsOn());
    }

    @Test
    void fixedDurationWithoutDurationIsRejected() {
        assertNotNull(request(1L, config(item(2L, FIXED_DURATION, null))).verifyDependsOn());
    }

    @Test
    void fixedDurationWithUnparsableDurationIsRejected() {
        assertNotNull(
                request(1L, config(item(2L, FIXED_DURATION, "not-a-duration"))).verifyDependsOn());
    }

    @Test
    void upstreamScheduleWithDurationIsRejected() {
        assertNotNull(request(1L, config(item(2L, UPSTREAM_SCHEDULE, "1h"))).verifyDependsOn());
    }

    @Test
    void neverWithoutDurationIsValid() {
        assertNull(request(1L, config(item(2L, NEVER, null))).verifyDependsOn());
    }

    @Test
    void neverWithDurationIsRejected() {
        assertNotNull(request(1L, config(item(2L, NEVER, "1h"))).verifyDependsOn());
    }

    private JobFlowRequest request(Long id, DependencyConfig... dependsOn) {
        var request = new JobFlowRequest();
        request.setId(id);
        if (dependsOn.length > 0) {
            request.setDependsOn(dependsOn[0]);
        }
        return request;
    }

    private DependencyConfig config(Dependency... items) {
        var cfg = new DependencyConfig();
        cfg.setPrecondition(ALL_MATCHED);
        cfg.setDependencies(Arrays.asList(items));
        return cfg;
    }

    private Dependency item(Long flowId, ExpiryPolicy expiry, String duration) {
        var item = new Dependency();
        item.setFlowId(flowId);
        item.setStatuses(List.of(SUCCESS));
        item.setExpiry(expiry);
        item.setDuration(duration);
        return item;
    }
}
