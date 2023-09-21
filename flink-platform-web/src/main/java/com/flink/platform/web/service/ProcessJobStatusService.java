package com.flink.platform.web.service;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.exception.JobStatusScrapeException;
import com.flink.platform.grpc.JobStatusReply;
import com.flink.platform.grpc.JobStatusRequest;
import com.flink.platform.web.config.AppRunner;
import com.flink.platform.web.monitor.DefaultStatusFetcher;
import com.flink.platform.web.monitor.StatusFetcher;
import com.flink.platform.web.util.ThreadUtil;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.flink.platform.common.enums.ExecutionStatus.ERROR;

/** Job status service. */
@Slf4j
@Service
public class ProcessJobStatusService {

    private final List<StatusFetcher> statusFetchers;

    private final DefaultStatusFetcher defaultStatusFetcher;

    @Autowired
    public ProcessJobStatusService(List<StatusFetcher> statusFetchers, DefaultStatusFetcher defaultStatusFetcher) {
        this.statusFetchers = statusFetchers;
        this.defaultStatusFetcher = defaultStatusFetcher;
    }

    @Nonnull
    public JobStatusReply getStatus(JobStatusRequest request) {
        final DeployMode deployMode = DeployMode.from(request.getDeployMode());
        final int retries = request.getRetries();
        StatusFetcher statusFetcher = statusFetchers.stream()
                .filter(fetcher -> fetcher.isSupported(deployMode))
                .findFirst()
                .orElseThrow(() -> new JobStatusScrapeException("No available job status fetcher"));

        int errorTimes = 0;
        while (AppRunner.isRunning()) {
            try {
                return statusFetcher.getStatus(request);
            } catch (Exception e) {
                log.error("Get job status failed, job run: {}, retry times: {}.", request.getJobRunId(), errorTimes, e);
                if (++errorTimes > retries) {
                    long currentTimeMillis = System.currentTimeMillis();
                    return JobStatusReply.newBuilder()
                            .setStatus(ERROR.getCode())
                            .setStartTime(currentTimeMillis)
                            .setEndTime(currentTimeMillis)
                            .build();
                }

                ThreadUtil.sleepRetry(errorTimes);
            }
        }

        return defaultStatusFetcher.getStatus(request);
    }
}
