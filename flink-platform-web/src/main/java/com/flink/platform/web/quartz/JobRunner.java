package com.flink.platform.web.quartz;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.service.ProcessJobService;
import com.flink.platform.web.service.WorkerApplyService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.springframework.web.client.RestTemplate;

import static com.flink.platform.web.util.HttpUtil.isRemoteUrl;

/** submit job. */
@Slf4j
public class JobRunner implements Job {

    private static final String REST_JOB_PROCESS = "/internal/process/%s";

    private final JobInfoService jobInfoService = SpringContext.getBean(JobInfoService.class);

    private final WorkerApplyService workerApplyService =
            SpringContext.getBean(WorkerApplyService.class);

    private final RestTemplate restTemplate = SpringContext.getBean(RestTemplate.class);

    private final ProcessJobService processJobService =
            SpringContext.getBean(ProcessJobService.class);

    @Override
    public void execute(JobExecutionContext context) {
        JobDetail detail = context.getJobDetail();
        JobKey key = detail.getKey();
        Long jobId = Long.parseLong(key.getName());

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
                return;
            }

            // Step 2: build cluster url, set localhost as default url if not specified.
            String routeUrl = workerApplyService.chooseWorker(jobInfo.getRouteUrl());

            // Step 3: process job.
            JobRunInfo jobRunInfo;
            if (isRemoteUrl(routeUrl)) {
                String httpUri = routeUrl + String.format(REST_JOB_PROCESS, jobId);
                jobRunInfo = restTemplate.getForObject(httpUri, JobRunInfo.class);
            } else {
                jobRunInfo = processJobService.processJob(jobId, null);
            }

            log.info("The job: {} is processed, job run id: {}", jobId, jobRunInfo.getId());
        } catch (Exception e) {
            log.error("Cannot exec job: {}", jobId, e);
        }
    }
}
