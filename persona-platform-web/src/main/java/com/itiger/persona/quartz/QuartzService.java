package com.itiger.persona.quartz;

import com.itiger.persona.comn.QuartzException;
import com.itiger.persona.entity.JobInfo;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
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
@Service
public class QuartzService {

    private static final String JOB_NAME = "jobName";

    @Resource(name = "quartzScheduler")
    Scheduler scheduler;

    /**
     * add trigger or throw Exception
     */
    public synchronized boolean addOrFailQuartzJob(JobInfo jobInfo) {
        try {
            checkQuartzStarted();
            boolean jobExists = isJobExists(jobInfo);
            boolean triggerExists = isTriggerExists(jobInfo);
            if (jobExists || triggerExists) {
                throw new QuartzException("job or trigger is already exists");
            }
            addTrigger(jobInfo);
            return true;
        } catch (Exception e) {
            throw new QuartzException("add quartz job failed", e);
        }
    }

    private boolean isTriggerExists(JobInfo jobInfo) throws SchedulerException {
        TriggerKey triggerKey = TriggerKey.triggerKey(jobInfo.getJobCode(), Key.DEFAULT_GROUP);
        CronTrigger trigger = (CronTrigger) scheduler.getTrigger(triggerKey);
        return trigger != null;
    }

    private boolean isJobExists(JobInfo jobInfo) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobInfo.getJobCode(), Key.DEFAULT_GROUP);
        JobDetail jobDetail = scheduler.getJobDetail(jobKey);
        return jobDetail != null;
    }

    private void addTrigger(JobInfo jobInfo) throws SchedulerException {
        JobDetail jobDetail = newJob(JobRunner.class)
                .withIdentity(jobInfo.getJobCode(), Key.DEFAULT_GROUP)
                .usingJobData(JOB_NAME, jobInfo.getJobName())
                .build();
        CronTrigger trigger = newTrigger()
                .withIdentity(jobInfo.getJobCode(), Key.DEFAULT_GROUP)
                .withSchedule(cronSchedule(jobInfo.getCronExpr()))
                .startNow()
                .build();
        scheduler.scheduleJob(jobDetail, trigger);
    }

    private void checkQuartzStarted() throws SchedulerException {
        if (!scheduler.isStarted()) {
            throw new QuartzException("quartz scheduler is not started");
        }
    }

}
