package com.flink.platform.web.quartz;

import com.flink.platform.common.util.ExceptionUtil;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.web.common.QuartzException;
import com.flink.platform.web.lifecycle.AppRunner;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

/** Quartz service. */
@Slf4j
@Service
public class QuartzService {

    private static final String GROUP_RUN_ONCE = "RUN_ONCE";

    @Resource(name = "quartzScheduler")
    Scheduler scheduler;

    public String quartzMetadata() {
        try {
            checkQuartzSchedulerStarted();
            return String.valueOf(scheduler.getMetaData());
        } catch (Exception e) {
            return ExceptionUtil.stackTrace(e);
        }
    }

    public Optional<LocalDateTime> nextTriggerTimeAfter(String cronExpr, LocalDateTime sinceTime) {
        return QuartzUtil.nextTriggerTimeAfter(cronExpr, sinceTime);
    }

    public boolean isValidCronExpression(String cronExpr) {
        return QuartzUtil.isValidExpression(cronExpr);
    }

    public boolean hasValidScheduleInterval(String cronExpr) throws Exception {
        return QuartzUtil.hasValidScheduleInterval(cronExpr);
    }

    public boolean scheduleJobFlow(JobFlow jobFlow) {
        return addJobToQuartz(new JobFlowQuartzSpec(jobFlow));
    }

    public void unscheduleJobFlow(JobFlow jobFlow) {
        removeJob(new JobFlowQuartzSpec(jobFlow));
    }

    public boolean runFlowOnce(JobFlow jobFlow, Map<String, Object> data) {
        return runOnce(new JobFlowQuartzSpec(jobFlow, data));
    }

    // ==================================================================
    // ========================= private ================================
    // ==================================================================

    /** add trigger or throw Exception. */
    private synchronized boolean addJobToQuartz(QuartzSpec spec) {
        try {
            checkQuartzSchedulerStarted();
            checkQuartzScheduleInterval(spec.getCron());

            var jobKey = spec.getJobKey();
            var triggerKey = spec.getTriggerKey();
            var jobExists = isJobExists(jobKey);
            var triggerExists = isTriggerExists(triggerKey);
            if (jobExists || triggerExists) {
                log.warn("Job or trigger is already exists, quartz spec: {}", spec);
                return false;
            }

            // schedule job.
            scheduleJob(spec);
            return true;
        } catch (Exception e) {
            throw new QuartzException("Add quartz job failed", e);
        }
    }

    private void removeJob(QuartzSpec spec) {
        deleteTrigger(spec.getTriggerKey());
        deleteJob(spec.getJobKey());
    }

    private synchronized boolean runOnce(QuartzSpec spec) {
        try {
            checkQuartzSchedulerStarted();

            var originJobKey = spec.getJobKey();
            var newJobGroup = String.join("_", originJobKey.getGroup(), GROUP_RUN_ONCE);
            var newJobKey = JobKey.jobKey(originJobKey.getName(), newJobGroup);

            var originTriggerKey = spec.getTriggerKey();
            var newTriggerGroup = String.join("_", originTriggerKey.getGroup(), GROUP_RUN_ONCE);
            var newTriggerKey = TriggerKey.triggerKey(originTriggerKey.getName(), newTriggerGroup);

            var jobDetail = newJob(spec.getJobClass())
                    .withIdentity(newJobKey)
                    .usingJobData(new JobDataMap(spec.getData()))
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

    private void deleteTrigger(TriggerKey triggerKey) {
        try {
            scheduler.pauseTrigger(triggerKey);
            scheduler.unscheduleJob(triggerKey);
        } catch (Exception e) {
            log.error("delete quartz trigger failed.", e);
        }
    }

    private void deleteJob(JobKey jobKey) {
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

    private void scheduleJob(QuartzSpec spec) throws SchedulerException {
        var jobDetail = newJob(spec.getJobClass())
                .withIdentity(spec.getJobKey())
                .usingJobData(new JobDataMap(spec.getData()))
                .build();
        var trigger = newTrigger()
                .withIdentity(spec.getTriggerKey())
                .withSchedule(cronSchedule(spec.getCron()))
                .startNow()
                .build();
        scheduler.scheduleJob(jobDetail, trigger);
    }

    private void checkQuartzSchedulerStarted() throws SchedulerException {
        if (!scheduler.isStarted() || AppRunner.isStopped()) {
            throw new QuartzException("quartz scheduler is not started");
        }
    }

    private void checkQuartzScheduleInterval(String cronExpr) throws Exception {
        if (!hasValidScheduleInterval(cronExpr)) {
            throw new QuartzException(" schedule interval must bigger than 1 minute");
        }
    }
}
