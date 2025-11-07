package com.flink.platform.web.monitor;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.grpc.JobStatusReply;
import com.flink.platform.web.environment.HadoopService;
import com.flink.platform.web.util.YarnHelper;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static com.flink.platform.common.enums.ExecutionStatus.NOT_EXIST;

/** status monitor. */
@Slf4j
@Order(1)
@Component
public class YarnStatusFetcher implements StatusFetcher {

    private final HadoopService hadoopService;

    @Autowired
    public YarnStatusFetcher(@Lazy HadoopService hadoopService) {
        this.hadoopService = hadoopService;
    }

    @Override
    public boolean isSupported(@Nonnull StatusRequest request) {
        return switch (request.getDeployMode()) {
            case FLINK_YARN_PER, FLINK_YARN_SESSION, FLINK_YARN_RUN_APPLICATION -> true;
            default -> false;
        };
    }

    @Override
    public JobStatusReply getStatus(@Nonnull StatusRequest request) {
        var applicationTag = YarnHelper.getApplicationTag(request.getJobRunId());
        try {
            var statusReport = hadoopService.getStatusReportWithRetry(applicationTag);
            if (statusReport != null) {
                return newJobStatusReply(
                        statusReport.getStatus(), statusReport.getStartTime(), statusReport.getFinishTime());
            } else {
                long currentTimeMillis = System.currentTimeMillis();
                return newJobStatusReply(NOT_EXIST, currentTimeMillis, currentTimeMillis);
            }
        } catch (Exception e) {
            log.error("Use yarn client to get ApplicationReport failed, application tag: {}", applicationTag, e);
            throw new RuntimeException(e);
        }
    }

    private JobStatusReply newJobStatusReply(ExecutionStatus status, long startTime, long endTime) {
        return JobStatusReply.newBuilder()
                .setStatus(status.getCode())
                .setStartTime(startTime)
                .setEndTime(endTime)
                .build();
    }
}
