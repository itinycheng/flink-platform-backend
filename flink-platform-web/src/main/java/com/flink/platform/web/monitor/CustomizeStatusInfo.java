package com.flink.platform.web.monitor;

import com.flink.platform.common.enums.ExecutionStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** Yarn status info. */
@Data
public class CustomizeStatusInfo implements StatusInfo {

    private final ExecutionStatus status;

    private final LocalDateTime startTime;

    private final LocalDateTime endTime;
}
