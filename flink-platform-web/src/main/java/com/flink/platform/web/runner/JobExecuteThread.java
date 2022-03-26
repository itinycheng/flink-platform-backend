package com.flink.platform.web.runner;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.config.AppRunner;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.monitor.CustomizeStatusInfo;
import com.flink.platform.web.monitor.StatusInfo;
import com.flink.platform.web.service.ProcessJobService;
import com.flink.platform.web.service.ProcessJobStatusService;
import com.flink.platform.web.util.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.Callable;

import static com.flink.platform.common.enums.ExecutionMode.STREAMING;
import static com.flink.platform.common.enums.ExecutionStatus.ERROR;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.NOT_EXIST;
import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;

/** Execute job in a separate thread. */
@Slf4j
public class JobExecuteThread implements Callable<JobResponse> {

    private static final String REST_JOB_PROCESS = "/internal/process/%s/%s";

    private static final String REST_GET_STATUS = "/internal/getStatus";

    private static final int MIN_SLEEP_TIME_MILLIS = 1000;

    private static final int MAX_SLEEP_TIME_MILLIS = 60_000;

    private final long flowRunId;

    private final JobVertex jobVertex;

    private final int errorRetries;

    private final int streamingJobToSuccessMills;

    private final JobInfoService jobInfoService;

    private final JobRunInfoService jobRunInfoService;

    private final RestTemplate restTemplate;

    private final ProcessJobService processJobService;

    private final ProcessJobStatusService processJobStatusService;

    public JobExecuteThread(long flowRunId, JobVertex jobVertex, WorkerConfig workerConfig) {
        this.flowRunId = flowRunId;
        this.jobVertex = jobVertex;
        this.errorRetries = workerConfig.getErrorRetries();
        this.streamingJobToSuccessMills = workerConfig.getStreamingJobToSuccessMills();
        this.jobInfoService = SpringContext.getBean(JobInfoService.class);
        this.processJobStatusService = SpringContext.getBean(ProcessJobStatusService.class);
        this.jobRunInfoService = SpringContext.getBean(JobRunInfoService.class);
        this.restTemplate = SpringContext.getBean(RestTemplate.class);
        this.processJobService = SpringContext.getBean(ProcessJobService.class);
    }

    @Override
    public JobResponse call() {
        Long jobId = jobVertex.getJobId();
        Long jobRunId = jobVertex.getJobRunId();

        try {
            // Step 1: get job info
            JobInfo jobInfo =
                    jobInfoService.getOne(
                            new QueryWrapper<JobInfo>()
                                    .lambda()
                                    .eq(JobInfo::getId, jobId)
                                    .eq(JobInfo::getStatus, JobStatus.ONLINE));

            if (jobInfo == null) {
                log.warn("The job:{} is no longer exists or not in ready/scheduled status.", jobId);
                return new JobResponse(jobId, jobRunId, NOT_EXIST);
            }

            // Step 2: build route url, set localhost as default url if not specified.
            String routeUrl = jobInfo.getRouteUrl();
            routeUrl = HttpUtil.getUrlOrDefault(routeUrl);

            // Step 3: process job and get jobRun.
            JobRunInfo jobRunInfo;
            if (jobRunId != null) {
                jobRunInfo = jobRunInfoService.getById(jobRunId);
                log.info("Job:{} already submitted, runId = {}.", jobId, jobRunId);
            } else {
                jobRunInfo = processRemoteJob(routeUrl, jobId);
            }

            if (jobRunInfo == null) {
                log.warn("The jobRun:{} is no longer exists.", jobRunId);
                return new JobResponse(jobId, jobRunId, NOT_EXIST);
            }

            // Step 4: Update jobRunId in Memory.
            jobRunId = jobRunInfo.getId();

            // Step 5: Wait for job complete and get final status.
            ExecutionStatus status = jobRunInfo.getStatus();
            if (status == null || !status.isTerminalState()) {
                StatusInfo statusInfo = waitForComplete(routeUrl, jobRunInfo);
                if (statusInfo != null) {
                    status = statusInfo.getStatus();
                    updateJobRunInfo(jobRunId, statusInfo.getStatus(), statusInfo.getEndTime());
                }
            }

            return new JobResponse(jobId, jobRunId, status);
        } catch (Exception e) {
            log.error("Submit job and wait for complete failed.", e);
            updateJobRunInfo(jobRunId, ERROR, LocalDateTime.now());
            return new JobResponse(jobId, jobRunId, ERROR);
        }
    }

    private void updateJobRunInfo(Long runId, ExecutionStatus status, LocalDateTime endTime) {
        try {
            if (runId == null) {
                return;
            }
            if (endTime == null) {
                endTime = LocalDateTime.now();
            }
            JobRunInfo jobRunInfo = new JobRunInfo();
            jobRunInfo.setId(runId);
            jobRunInfo.setStatus(status);
            jobRunInfo.setStopTime(endTime);
            jobRunInfoService.updateById(jobRunInfo);
        } catch (Exception ignored) {
        }
    }

    /** Send request to process remote job. */
    private JobRunInfo processRemoteJob(String routeUrl, long jobId) {
        int retryTimes = 0;
        while (++retryTimes <= errorRetries) {
            try {
                if (isRemoteUrl(routeUrl)) {
                    String httpUri = routeUrl + String.format(REST_JOB_PROCESS, jobId, flowRunId);
                    return restTemplate.getForObject(httpUri, JobRunInfo.class);
                } else {
                    return processJobService.processJob(jobId, flowRunId);
                }
            } catch (Exception e) {
                log.error("Process job: {} failed.", jobId);
                sleep(retryTimes);
            }
        }

        throw new IllegalStateException("Times to submit job exceeded limit: " + errorRetries);
    }

    public StatusInfo waitForComplete(String routeUrl, JobRunInfo jobRunInfo) {
        int retryTimes = 0;
        int errorTimes = 0;
        boolean isRemote = isRemoteUrl(routeUrl);

        while (AppRunner.isRunning()) {
            try {
                StatusInfo statusInfo;
                if (isRemote) {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<JobRunInfo> requestEntity = new HttpEntity<>(jobRunInfo, headers);
                    statusInfo =
                            restTemplate.postForObject(
                                    routeUrl + REST_GET_STATUS, requestEntity, StatusInfo.class);
                } else {
                    statusInfo = processJobStatusService.getStatus(jobRunInfo);
                }

                if (jobRunInfo.getExecMode() == STREAMING) {
                    if (jobRunInfo.getCreateTime() == null) {
                        jobRunInfo.setCreateTime(LocalDateTime.now());
                    }
                    statusInfo =
                            updateAndGetStreamJobStatus(statusInfo, jobRunInfo.getCreateTime());
                }

                if (statusInfo != null && statusInfo.getStatus().isTerminalState()) {
                    return statusInfo;
                }

            } catch (Exception e) {
                if (++errorTimes > errorRetries) {
                    return new CustomizeStatusInfo(ERROR, LocalDateTime.now(), LocalDateTime.now());
                }
            }

            sleep(++retryTimes);
        }

        return null;
    }

    private StatusInfo updateAndGetStreamJobStatus(StatusInfo statusInfo, LocalDateTime startTime) {
        if (statusInfo == null) {
            return null;
        }

        if (statusInfo.getStatus().isTerminalState()) {
            return statusInfo;
        }

        if (LocalDateTime.now().isAfter(startTime.plusMinutes(streamingJobToSuccessMills))) {
            if (statusInfo.getStatus() == RUNNING) {
                return new CustomizeStatusInfo(
                        SUCCESS, statusInfo.getStartTime(), statusInfo.getEndTime());
            } else {
                return new CustomizeStatusInfo(
                        FAILURE, statusInfo.getStartTime(), statusInfo.getEndTime());
            }
        }

        return statusInfo;
    }

    private boolean isRemoteUrl(String routeUrl) {
        return !routeUrl.contains(HttpUtil.LOCALHOST_URL);
    }

    private void sleep(int retryTimes) {
        try {
            Thread.sleep(Math.min(retryTimes * MIN_SLEEP_TIME_MILLIS, MAX_SLEEP_TIME_MILLIS));
        } catch (Exception ignored) {
        }
    }
}
