package com.flink.platform.common.test;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.util.JsonUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the KILLABLE -> KILLING rename stays backward compatible with existing data / wire formats. */
class ExecutionStatusTest {

    @Test
    void grpcCodeUnchanged() {
        // gRPC uses the int code; KILLING must keep KILLABLE's old code (9) so peers stay compatible.
        assertEquals(9, ExecutionStatus.KILLING.getCode());
        assertEquals(ExecutionStatus.KILLING, ExecutionStatus.from(9));
    }

    @Test
    void classification() {
        assertFalse(ExecutionStatus.KILLING.isTerminalState());
        assertTrue(ExecutionStatus.KILLING.isTransient());
        // Must remain in NON_TERMINALS so recovery / kill queries keep matching it.
        assertTrue(ExecutionStatus.getNonTerminals().contains(ExecutionStatus.KILLING));
    }

    @Test
    void serializesAsKilling() {
        assertEquals("\"KILLING\"", JsonUtil.toJsonString(ExecutionStatus.KILLING));
    }

    @Test
    void deserializesLegacyKillableAndNewKilling() {
        // Existing JSON blobs (AlertConfig.statuses, serialized DAGs, ...) still hold the legacy "KILLABLE".
        assertEquals(ExecutionStatus.KILLING, JsonUtil.toBean("\"KILLABLE\"", ExecutionStatus.class));
        assertEquals(ExecutionStatus.KILLING, JsonUtil.toBean("\"KILLING\"", ExecutionStatus.class));
    }

    @Test
    void otherStatusesUnaffected() {
        assertEquals("\"SUCCESS\"", JsonUtil.toJsonString(ExecutionStatus.SUCCESS));
        assertEquals(ExecutionStatus.SUCCESS, JsonUtil.toBean("\"SUCCESS\"", ExecutionStatus.class));
    }
}
