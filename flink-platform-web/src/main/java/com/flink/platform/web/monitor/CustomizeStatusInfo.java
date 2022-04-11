package com.flink.platform.web.monitor;

import com.flink.platform.common.enums.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Yarn status info. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomizeStatusInfo implements StatusInfo {

    private ExecutionStatus status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
