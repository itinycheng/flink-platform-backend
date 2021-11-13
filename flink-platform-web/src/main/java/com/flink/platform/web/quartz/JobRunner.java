package com.flink.platform.web.quartz;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.util.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** submit job. */
@Slf4j
public class JobRunner implements Job {

    private static final String JOB_PROCESS_REST_PATH = "/internal/process/%s";

    private static final Map<String, Long> RUNNER_MAP = new ConcurrentHashMap<>();

    private final JobInfoService jobInfoService = SpringContext.getBean(JobInfoService.class);

    private final RestTemplate restTemplate = SpringContext.getBean(RestTemplate.class);

    @Override
    public void execute(JobExecutionContext context) {
        JobDetail detail = context.getJobDetail();
        JobKey key = detail.getKey();
        String code = key.getName();

        try {
            // TODO Avoid preforming the same job multiple times at the same time.
            Long previous = RUNNER_MAP.putIfAbsent(code, System.currentTimeMillis());
            if (previous != null && previous > 0) {
                log.warn("The job: {} is already running, start time: {}", code, previous);
                return;
            }

            // Step 1: get job info
            JobInfo jobInfo =
                    jobInfoService.getOne(
                            new QueryWrapper<JobInfo>()
                                    .lambda()
                                    .eq(JobInfo::getCode, code)
                                    .in(
                                            JobInfo::getStatus,
                                            JobStatus.SCHEDULED.getCode(),
                                            JobStatus.READY.getCode()));
            if (jobInfo == null) {
                log.warn("The job is no longer exists or not in ready/scheduled status, {}", code);
                return;
            }

            // Step 2: build cluster url, set localhost as default url if not specified.
            String routeUrl = jobInfo.getRouteUrl();
            routeUrl = HttpUtil.getUrlOrDefault(routeUrl);

            // Step 3: send http request.
            String httpUri = routeUrl + String.format(JOB_PROCESS_REST_PATH, code);
            ResultInfo<Long> response =
                    restTemplate
                            .exchange(
                                    httpUri,
                                    HttpMethod.GET,
                                    null,
                                    new ParameterizedTypeReference<ResultInfo<Long>>() {})
                            .getBody();
            log.info("The job: {} is processed, job run result: {}", code, response);

        } catch (Exception e) {
            log.error("Cannot exec job: {}", code, e);
        } finally {
            RUNNER_MAP.remove(code);
        }
    }
}
