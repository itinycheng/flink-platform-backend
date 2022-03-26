package com.flink.platform.web.monitor;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.dao.entity.JobRunInfo;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

import java.time.LocalDateTime;

/** status monitor. */
@Component
public class DefaultStatusFetcher implements StatusFetcher {

    public boolean isSupported(DeployMode deployMode) {
        return false;
    }

    public StatusInfo getStatus(JobRunInfo jobRunInfo) {
        return new StatusInfo() {
            @Nonnull
            @Override
            public ExecutionStatus getStatus() {
                return ExecutionStatus.SUBMITTED;
            }

            @Override
            public LocalDateTime getStartTime() {
                return null;
            }

            @Override
            public LocalDateTime getEndTime() {
                return null;
            }
        };
    }
}
