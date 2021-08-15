package com.itiger.persona.service;

import com.itiger.persona.common.util.DateUtil;
import com.itiger.persona.comn.QuartzException;
import com.itiger.persona.entity.JobInfo;
import com.itiger.persona.quartz.JobRunner;
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
        try {
            // 通过触发器名和组名获取TriggerKey
            TriggerKey triggerKey = TriggerKey.triggerKey(jobCode, Key.DEFAULT_GROUP);
            // 通过任务名和组名获取JobKey
            JobKey jobKey = JobKey.jobKey(jobCode, Key.DEFAULT_GROUP);
            // 停止触发器
            scheduler.pauseTrigger(triggerKey);
            // 移除触发器
            scheduler.unscheduleJob(triggerKey);
            // 删除任务
            scheduler.deleteJob(jobKey);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
