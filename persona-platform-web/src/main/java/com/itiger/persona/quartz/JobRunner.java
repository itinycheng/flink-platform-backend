package com.itiger.persona.quartz;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itiger.persona.comn.SpringContext;
import com.itiger.persona.entity.JobInfo;
import com.itiger.persona.service.IJobInfoService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * submit job
 *
 * @author tiny.wang
 */
@Slf4j
public class JobRunner implements Job {

    private static final JobInfo JOB_INFO = new JobInfo();

    private static final Map<String, Long> RUNNER_MAP = new ConcurrentHashMap<>();

    private static final ApplicationContext SPRING_CONTEXT = SpringContext.getApplicationContext();

    @Override
    public void execute(JobExecutionContext context) {
        JobDetail detail = context.getJobDetail();
        JobKey key = detail.getKey();
        String code = key.getName();
        try {
            // avoid preforming the same job multiple times at the same time
            Long previous = RUNNER_MAP.putIfAbsent(code, System.currentTimeMillis());
            if (previous != null && previous > 0) {
                log.warn("the job: {} is already running, start time: {}", code, previous);
                return;
            }
            // step 1: get job info
            IJobInfoService jobInfoService = SPRING_CONTEXT.getBean(IJobInfoService.class);
            JobInfo jobInfo = jobInfoService.getOne(new QueryWrapper<JobInfo>().lambda().eq(JobInfo::getCode, code));
            if (jobInfo == null) {
                log.warn("the job: {} is no longer exists", code);
                return;
            }
            // step 2: convert to SqlContext

            // build shell command

            // step 3: submit job

        } catch (Exception e) {
            log.error("cannot exec job: {}", code, e);
        } finally {
            RUNNER_MAP.remove(code);
        }
        log.info(" job key: {}, current time: {}", key, System.currentTimeMillis());
    }
}