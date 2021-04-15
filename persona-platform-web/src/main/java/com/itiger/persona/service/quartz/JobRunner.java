package com.itiger.persona.service.quartz;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;

/**
 * @author tiger
 */
@Slf4j
public class JobRunner implements Job {

    @Override
    public void execute(JobExecutionContext context) {
        JobDetail detail = context.getJobDetail();
        JobKey key = detail.getKey();
        // TODO avoid preforming the same job multiple times at the same time.
        log.info(" job key: {}, current time: {}", key, System.currentTimeMillis());
    }
}