package com.flink.platform.web.monitor;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.grpc.JobStatusReply;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static com.flink.platform.common.util.DateUtil.toLocalDateTime;

/** status info. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusInfo {

    private ExecutionStatus status;

    private Long startTime;

    private Long endTime;

    public LocalDateTime toEndTime() {
        if (endTime == null || endTime == 0) {
            return null;
        }
        return toLocalDateTime(endTime);
    }

    public static StatusInfo fromReplay(JobStatusReply replay) {
        StatusInfo statusInfo = new StatusInfo();
        statusInfo.setStatus(ExecutionStatus.from(replay.getStatus()));

        if (replay.hasStartTime()) {
            statusInfo.setStartTime(replay.getStartTime());
        }

        if (replay.hasEndTime()) {
            statusInfo.setEndTime(replay.getEndTime());
        }

        return statusInfo;
    }
}
