package com.flink.platform.web.service;

import com.flink.platform.common.util.DateUtil;
import com.flink.platform.web.comn.QuartzException;
import com.flink.platform.web.entity.JobInfo;
import com.flink.platform.web.quartz.JobRunner;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
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
import java.text.ParseException;
import java.util.Date;

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
            checkQuartzScheduleInterval(jobInfo.getCronExpr());
            boolean jobExists = isJobExists(jobInfo);
            boolean triggerExists = isTriggerExists(jobInfo);
            if (jobExists || triggerExists) {
                log.warn("job or trigger is already exists, jobCode: {}", jobInfo.getCode());
            } else {
                addTrigger(jobInfo);
                added = true;
            }
        } catch (Exception e) {
            throw new QuartzException("add quartz job failed", e);
        }
        return added;
    }

    /**
     * 移除一个任务
     *
     * @param jobCode 任务名
     */
    public void removeJob(String jobCode) {
        deleteTrigger(jobCode, Key.DEFAULT_GROUP);
        deleteJob(jobCode, Key.DEFAULT_GROUP);
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

    public void deleteTrigger(String name, String group) {
        try {
            TriggerKey triggerKey = TriggerKey.triggerKey(name, group);
            scheduler.pauseTrigger(triggerKey);
            scheduler.unscheduleJob(triggerKey);
        } catch (Exception e) {
            log.error("delete quartz trigger failed.", e);
        }
    }

    public void deleteJob(String name, String group) {
        try {
            JobKey jobKey = JobKey.jobKey(name, group);
            scheduler.deleteJob(jobKey);
        } catch (Exception e) {
            log.error("delete quartz job failed.", e);
        }
    }

    private boolean isTriggerExists(JobInfo jobInfo) throws SchedulerException {
        TriggerKey triggerKey = TriggerKey.triggerKey(jobInfo.getCode(), Key.DEFAULT_GROUP);
        Trigger trigger = scheduler.getTrigger(triggerKey);
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

    public static void checkQuartzScheduleInterval(String cronExpr) throws ParseException {
        CronExpression cronExpression = new CronExpression(cronExpr);
        Date validTime1 = cronExpression.getNextValidTimeAfter(new Date());
        Date validTime2 = cronExpression.getNextValidTimeAfter(validTime1);
        if (validTime2.getTime() - validTime1.getTime() < DateUtil.MILLIS_PER_MINUTE) {
            throw new QuartzException(" schedule interval must bigger than 1 minute");
        }
    }

}
