package com.flink.platform.dao.entity;

import com.flink.platform.common.util.JsonUtil;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.flink.platform.common.enums.ExecutionStrategy.ALL_POST_JOBS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionConfigTest {

    @Test
    void usesEffectiveDefaultForActiveRuns() {
        var config = new ExecutionConfig();

        assertEquals(0, config.getMaxActiveRuns());
        assertEquals(1, config.defaultOrGetMaxActiveRuns());
    }

    @Test
    void writesCanonicalJobLimitNameOnly() {
        var config = new ExecutionConfig();
        config.setMaxJobsPerRun(5);

        var json = JsonUtil.toJsonString(config);

        assertTrue(json.contains("\"maxJobsPerRun\":5"));
        assertFalse(json.contains("\"parallelism\""));
    }

    @Test
    void readsNewJobLimitName() {
        var config = JsonUtil.toBean("{\"maxJobsPerRun\":5}", ExecutionConfig.class);

        assertEquals(5, config.getMaxJobsPerRun());
    }

    @Test
    void readsLegacyJobLimitNameFromStoredJson() {
        var config = JsonUtil.toBean("{\"parallelism\":5}", ExecutionConfig.class);

        assertEquals(5, config.getMaxJobsPerRun());
    }

    @Test
    void mergeFromOverridesOnlyExplicitlyConfiguredValues() {
        var scheduleTime = LocalDateTime.of(2026, 10, 5, 10, 0);
        var config = new ExecutionConfig();
        config.setStartJobId(10L);
        config.setScheduleTime(scheduleTime);
        config.setMaxActiveRuns(3);
        config.setMaxJobsPerRun(5);

        var other = new ExecutionConfig();
        other.setStrategy(ALL_POST_JOBS);
        other.setStartJobId(20L);
        other.setMaxActiveRuns(4);

        config.mergeFrom(other);

        assertEquals(ALL_POST_JOBS, config.getStrategy());
        assertEquals(20L, config.getStartJobId());
        assertEquals(scheduleTime, config.getScheduleTime());
        assertEquals(4, config.getMaxActiveRuns());
        assertEquals(5, config.getMaxJobsPerRun());
    }

    @Test
    void mergeFromNullLeavesConfigUnchanged() {
        var config = new ExecutionConfig();
        config.setMaxActiveRuns(3);

        config.mergeFrom(null);

        assertEquals(3, config.getMaxActiveRuns());
    }
}
