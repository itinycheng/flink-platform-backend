package com.flink.platform.web.dto.request;

import com.flink.platform.common.util.JsonUtil;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.flink.platform.common.enums.ExecutionStrategy.ONLY_CUR_JOB;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JobFlowExecRequestTest {

    @Test
    void executionInputsAreDeserialized() {
        var request = JsonUtil.toBean("""
                {
                  "config": {"strategy": "ONLY_CUR_JOB", "startJobId": 42},
                  "scheduleTime": "2026-10-05 10:00:00"
                }
                """, JobFlowExecRequest.class);

        assertEquals(ONLY_CUR_JOB, request.getConfig().getStrategy());
        assertEquals(42L, request.getConfig().getStartJobId());
        assertEquals(LocalDateTime.of(2026, 10, 5, 10, 0), request.getScheduleTime());
    }
}
