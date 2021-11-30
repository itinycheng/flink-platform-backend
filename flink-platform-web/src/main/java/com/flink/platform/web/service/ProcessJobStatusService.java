package com.flink.platform.web.service;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.exception.JobStatusScrapeException;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.JobCallback;
import com.flink.platform.web.monitor.StatusFetcher;
import com.flink.platform.web.monitor.StatusInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/** Job status service. */
@Slf4j
@Service
public class ProcessJobStatusService {

    private final JobRunInfoService jobRunInfoService;

    private final List<StatusFetcher> statusFetchers;

    @Autowired
    public ProcessJobStatusService(
            JobRunInfoService jobRunInfoService, List<StatusFetcher> statusFetchers) {
        this.jobRunInfoService = jobRunInfoService;
        this.statusFetchers = statusFetchers;
    }

    public void updateStatus(final List<Long> jobInstanceIdList) {
        for (Long jobInstanceId : jobInstanceIdList) {
            JobRunInfo jobRunInfo = jobRunInfoService.getById(jobInstanceId);
            if (jobRunInfo == null) {
                log.warn("No Job instance found, instanceId: {}", jobInstanceId);
                continue;
            }

            JobCallback jobCallback = JsonUtil.toBean(jobRunInfo.getBackInfo(), JobCallback.class);
            if (jobCallback == null) {
                log.warn(
                        "Callback info of the job instance wasn't found, instanceId: {}",
                        jobInstanceId);
                continue;
            }

            final DeployMode deployMode = jobRunInfo.getDeployMode();
            StatusInfo statusInfo =
                    statusFetchers.stream()
                            .filter(fetcher -> fetcher.isSupported(deployMode))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new JobStatusScrapeException(
                                                    "No available job status fetcher"))
                            .getStatus(jobRunInfo);

            if (statusInfo == null) {
                continue;
            }

            // update job status
            JobRunInfo runInfo =
                    JobRunInfo.builder()
                            .id(jobRunInfo.getId())
                            .status(statusInfo.getStatus().getCode())
                            .build();
            jobRunInfoService.updateById(runInfo);
        }
    }
}
