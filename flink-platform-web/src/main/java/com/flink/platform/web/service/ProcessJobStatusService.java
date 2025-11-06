package com.flink.platform.web.service;

import com.flink.platform.common.exception.JobStatusScrapeException;
import com.flink.platform.grpc.JobStatusReply;
import com.flink.platform.grpc.JobStatusRequest;
import com.flink.platform.web.monitor.DefaultStatusFetcher;
import com.flink.platform.web.monitor.StatusFetcher;
import com.flink.platform.web.monitor.StatusRequest;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/** Job status service. */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ProcessJobStatusService {

    private final List<StatusFetcher> statusFetchers;

    private final DefaultStatusFetcher defaultStatusFetcher;

    @Nonnull
    public JobStatusReply getStatus(JobStatusRequest jobStatusRequest) {
        var request = new StatusRequest(jobStatusRequest);
        StatusFetcher statusFetcher = statusFetchers.stream()
                .filter(fetcher -> fetcher.isSupported(request))
                .findFirst()
                .orElseThrow(() -> new JobStatusScrapeException("No available job status fetcher"));

        try {
            return statusFetcher.getStatus(request);
        } catch (Exception e) {
            log.error("Get job status failed, job run: {}", request.getJobRunId(), e);
            return defaultStatusFetcher.getStatus(request);
        }
    }
}
