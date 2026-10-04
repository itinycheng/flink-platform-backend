package com.flink.platform.web.dto.request;

import com.flink.platform.common.enums.ExpiryPolicy;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.Dependency;
import com.flink.platform.dao.entity.DependencyConfig;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.config.WorkerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.flink.platform.common.enums.ExecutionCondition.ALL_DONE;
import static com.flink.platform.common.enums.ExecutionCondition.ALL_MATCHED;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.common.enums.ExpiryPolicy.FIXED_DURATION;
import static com.flink.platform.common.enums.ExpiryPolicy.NEVER;
import static com.flink.platform.common.enums.ExpiryPolicy.UPSTREAM_SCHEDULE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Verifies prerequisite-dependency validation of {@link JobFlowRequest}. */
@ExtendWith(MockitoExtension.class)
class JobFlowRequestTest {

    @Mock
    private JobFlowService jobFlowService;

    @Mock
    private WorkerConfig workerConfig;

    private MockedStatic<SpringContext> springContext;

    @BeforeEach
    void setUp() {
        springContext = mockStatic(SpringContext.class);
        springContext.when(() -> SpringContext.getBean(JobFlowService.class)).thenReturn(jobFlowService);
        springContext.when(() -> SpringContext.getBean(WorkerConfig.class)).thenReturn(workerConfig);
        lenient().when(workerConfig.getMaxActiveRunsPerFlow()).thenReturn(10);
        lenient().when(jobFlowService.listExistingFlowIds(anySet())).thenAnswer(invocation -> {
            Collection<Long> flowIds = invocation.getArgument(0);
            return Set.copyOf(flowIds);
        });
    }

    @AfterEach
    void tearDown() {
        springContext.close();
    }

    @Test
    void validCronExpressionIsAccepted() {
        var request = new JobFlowRequest();
        request.setCronExpr("0 0/10 * * * ?");

        assertNull(request.verifyCronExpr());
    }

    @Test
    void invalidOrTooFrequentCronExpressionIsRejected() {
        var request = new JobFlowRequest();
        request.setCronExpr("invalid");
        assertNotNull(request.verifyCronExpr());

        request.setCronExpr("0/30 * * * * ?");
        assertNotNull(request.verifyCronExpr());
    }

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
    void nullDependencyIsRejected() {
        assertNotNull(request(1L, config((Dependency) null)).verifyDependsOn());
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

    @Test
    void missingDependencyIsRejected() {
        when(jobFlowService.listExistingFlowIds(anySet())).thenReturn(Set.of());

        assertEquals(
                "Dependency workflow not found: [2]",
                request(1L, config(item(2L, NEVER, null))).verifyDependsOn());
    }

    @Test
    void maxActiveRunsCannotExceedPlatformLimit() {
        var request = new JobFlowRequest();
        var executionConfig = new ExecutionConfig();
        executionConfig.setMaxJobsPerRun(1);
        executionConfig.setMaxActiveRuns(11);
        request.setConfig(executionConfig);

        assertEquals("The range of maxActiveRuns is 1 ~ 10", request.verifyMaxActiveRuns());
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
