package com.flink.platform.web.service;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.exception.JobStatusScrapeException;
import com.flink.platform.grpc.JobStatusReply;
import com.flink.platform.grpc.JobStatusRequest;
import com.flink.platform.web.monitor.StatusFetcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;

import java.util.List;

/** Job status service. */
@Slf4j
@Service
public class ProcessJobStatusService {

    private final List<StatusFetcher> statusFetchers;

    @Autowired
    public ProcessJobStatusService(List<StatusFetcher> statusFetchers) {
        this.statusFetchers = statusFetchers;
    }

    @Nonnull
    public JobStatusReply getStatus(JobStatusRequest request) {
        final DeployMode deployMode = DeployMode.from(request.getDeployMode());
        return statusFetchers.stream()
                .filter(fetcher -> fetcher.isSupported(deployMode))
                .findFirst()
                .orElseThrow(() -> new JobStatusScrapeException("No available job status fetcher"))
                .getStatus(request);
    }
}
