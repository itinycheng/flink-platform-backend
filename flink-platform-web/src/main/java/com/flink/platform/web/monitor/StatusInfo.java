package com.flink.platform.web.monitor;

import com.flink.platform.common.enums.ExecutionStatus;

import javax.annotation.Nonnull;

import java.time.LocalDateTime;

/** status info. */
public interface StatusInfo {

    @Nonnull
    ExecutionStatus getStatus();

    LocalDateTime getStartTime();

    LocalDateTime getEndTime();
}
