package com.flink.platform.web.monitor;

import com.flink.platform.common.enums.ExecutionStatus;

import java.time.LocalDateTime;

/** status info. */
public interface StatusInfo {

    ExecutionStatus getStatus();

    LocalDateTime getStartTime();

    LocalDateTime getEndTime();
}
