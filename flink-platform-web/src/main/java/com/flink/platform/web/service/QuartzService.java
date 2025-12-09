package com.flink.platform.web.service;

import com.flink.platform.common.util.DateUtil;
import com.flink.platform.common.util.ExceptionUtil;
import com.flink.platform.web.common.QuartzException;
import com.flink.platform.web.config.AppRunner;
import com.flink.platform.web.entity.IQuartzInfo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Date;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

/** Job info quartz service. */
@Slf4j
@Service
public class QuartzService {

    private static final String GROUP_RUN_ONCE = "RUN_ONCE";

    @Resource(name = "quartzScheduler")
    Scheduler scheduler;

    public void waitForStarted() {
        try {
            int retry = 10;
            while (--retry > 0) {
                if (scheduler.isStarted()) {
                    return;
                } else {
                    Thread.sleep(10_000);
                }
            }
        } catch (Exception e) {
            throw new QuartzException("Waiting for quartz start failed", e);
        }
    }

    public String quartzMetadata() {
        try {
            checkQuartzSchedulerStarted();
            return String.valueOf(scheduler.getMetaData());
        } catch (Exception e) {
            return ExceptionUtil.stackTrace(e);
        }
    }

    /** add trigger or throw Exception. */
    public synchronized boolean addJobToQuartz(IQuartzInfo quartzInfo) {
        try {
            checkQuartzSchedulerStarted();
            checkQuartzScheduleInterval(quartzInfo.getCron());

            var jobKey = quartzInfo.getJobKey();
            var triggerKey = quartzInfo.getTriggerKey();
            var jobExists = isJobExists(jobKey);
            var triggerExists = isTriggerExists(triggerKey);
            if (jobExists || triggerExists) {
                log.warn("Job or trigger is already exists, quartz info: {}", quartzInfo);
                return false;
            }

            // schedule job.
            scheduleJob(quartzInfo);
            return true;
        } catch (Exception e) {
            throw new QuartzException("Add quartz job failed", e);
        }
    }

    public void removeJob(IQuartzInfo quartzInfo) {
        deleteTrigger(quartzInfo.getTriggerKey());
        deleteJob(quartzInfo.getJobKey());
    }

    public synchronized boolean runOnce(IQuartzInfo quartzInfo) {
        try {
            checkQuartzSchedulerStarted();

            var originJobKey = quartzInfo.getJobKey();
            var newJobGroup = String.join("_", originJobKey.getGroup(), GROUP_RUN_ONCE);
            var newJobKey = JobKey.jobKey(originJobKey.getName(), newJobGroup);

            var originTriggerKey = quartzInfo.getTriggerKey();
            var newTriggerGroup = String.join("_", originTriggerKey.getGroup(), GROUP_RUN_ONCE);
            var newTriggerKey = TriggerKey.triggerKey(originTriggerKey.getName(), newTriggerGroup);

            var jobDetail = newJob(quartzInfo.getJobClass())
                    .withIdentity(newJobKey)
                    .usingJobData(new JobDataMap(quartzInfo.getData()))
                    .build();
            var simpleTrigger = TriggerBuilder.newTrigger()
                    .withIdentity(newTriggerKey)
                    .startNow()
                    .build();
            scheduler.scheduleJob(jobDetail, simpleTrigger);
            return true;
        } catch (Exception e) {
            throw new QuartzException("Failed to run quartz job once time", e);
        }
    }

    public void deleteTrigger(TriggerKey triggerKey) {
        try {
            scheduler.pauseTrigger(triggerKey);
            scheduler.unscheduleJob(triggerKey);
        } catch (Exception e) {
            log.error("delete quartz trigger failed.", e);
        }
    }

    public void deleteJob(JobKey jobKey) {
        try {
            scheduler.deleteJob(jobKey);
        } catch (Exception e) {
            log.error("delete quartz job failed.", e);
        }
    }

    private boolean isTriggerExists(TriggerKey triggerKey) throws SchedulerException {
        var trigger = scheduler.getTrigger(triggerKey);
        return trigger != null;
    }

    private boolean isJobExists(JobKey jobKey) throws SchedulerException {
        var jobDetail = scheduler.getJobDetail(jobKey);
        return jobDetail != null;
    }

    private void scheduleJob(IQuartzInfo quartzInfo) throws SchedulerException {
        var jobDetail = newJob(quartzInfo.getJobClass())
                .withIdentity(quartzInfo.getJobKey())
                .usingJobData(new JobDataMap(quartzInfo.getData()))
                .build();
        var trigger = newTrigger()
                .withIdentity(quartzInfo.getTriggerKey())
                .withSchedule(cronSchedule(quartzInfo.getCron()))
                .startNow()
                .build();
        scheduler.scheduleJob(jobDetail, trigger);
    }

    private void checkQuartzSchedulerStarted() throws SchedulerException {
        if (!scheduler.isStarted() || AppRunner.isStopped()) {
            throw new QuartzException("quartz scheduler is not started");
        }
    }

    private void checkQuartzScheduleInterval(String cronExpr) throws ParseException {
        var cronExpression = new CronExpression(cronExpr);
        var validTime1 = cronExpression.getNextValidTimeAfter(new Date());
        var validTime2 = cronExpression.getNextValidTimeAfter(validTime1);
        if (validTime2.getTime() - validTime1.getTime() < DateUtil.MILLIS_PER_MINUTE) {
            throw new QuartzException(" schedule interval must bigger than 1 minute");
        }
    }
}
