package com.itiger.persona.service;

import com.itiger.persona.comn.QuartzException;
import com.itiger.persona.entity.JobInfo;
import com.itiger.persona.quartz.JobRunner;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.utils.Key;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * @author tiny.wang
 */
@Slf4j
@Service
public class JobInfoQuartzService {

    private static final String JOB_NAME = "jobName";

    private static final String GROUP_RUN_ONCE = "RUN_ONCE";

    @Resource(name = "quartzScheduler")
    Scheduler scheduler;

    /**
     * add trigger or throw Exception
     */
    public synchronized boolean addJobToQuartz(JobInfo jobInfo) {
        boolean added = false;
        try {
            checkQuartzSchedulerStarted();
            boolean jobExists = isJobExists(jobInfo);
            boolean triggerExists = isTriggerExists(jobInfo);
            if (jobExists || triggerExists) {
                log.warn("job or trigger is already exists, jobCode: {}", jobInfo.getCode());
            } else {
                addTrigger(jobInfo);
                added = true;
            }
        } catch (Exception e) {
            log.error("add quartz job failed", e);
        }
        return added;
    }

    public synchronized boolean runOnce(JobInfo jobInfo) {
        try {
            checkQuartzSchedulerStarted();
            JobDetail jobDetail = newJob(JobRunner.class)
                    .withIdentity(jobInfo.getCode(), GROUP_RUN_ONCE)
                    .usingJobData(JOB_NAME, jobInfo.getName())
                    .build();
            Trigger simpleTrigger = TriggerBuilder.newTrigger()
                    .withIdentity(jobInfo.getCode(), GROUP_RUN_ONCE)
                    .startNow().build();
            scheduler.scheduleJob(jobDetail, simpleTrigger);
            return true;
        } catch (Exception e) {
            log.error("failed to run quartz job once time", e);
            return false;
        }
    }

    private boolean isTriggerExists(JobInfo jobInfo) throws SchedulerException {
        TriggerKey triggerKey = TriggerKey.triggerKey(jobInfo.getCode(), Key.DEFAULT_GROUP);
        CronTrigger trigger = (CronTrigger) scheduler.getTrigger(triggerKey);
        return trigger != null;
    }

    private boolean isJobExists(JobInfo jobInfo) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobInfo.getCode(), Key.DEFAULT_GROUP);
        JobDetail jobDetail = scheduler.getJobDetail(jobKey);
        return jobDetail != null;
    }

    private void addTrigger(JobInfo jobInfo) throws SchedulerException {
        JobDetail jobDetail = newJob(JobRunner.class)
                .withIdentity(jobInfo.getCode(), Key.DEFAULT_GROUP)
                .usingJobData(JOB_NAME, jobInfo.getName())
                .build();
        CronTrigger trigger = newTrigger()
                .withIdentity(jobInfo.getCode(), Key.DEFAULT_GROUP)
                .withSchedule(cronSchedule(jobInfo.getCronExpr()))
                .startNow()
                .build();
        scheduler.scheduleJob(jobDetail, trigger);
    }

    private void checkQuartzSchedulerStarted() throws SchedulerException {
        if (!scheduler.isStarted()) {
            throw new QuartzException("quartz scheduler is not started");
        }
    }

}
