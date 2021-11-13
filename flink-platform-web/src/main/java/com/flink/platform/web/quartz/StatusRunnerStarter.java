package com.flink.platform.web.quartz;

import com.flink.platform.common.constants.Constant;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import java.util.concurrent.CompletableFuture;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;

/** Status runner starter. */
@Slf4j
@Component
public class StatusRunnerStarter {

    private static final String KEY_NAME = "STATUS_RUNNER";

    private static final String KEY_GROUP = "STATUS_GROUP";

    private static final String HOST_NAME = "hostname";

    private static final JobKey JOB_KEY = JobKey.jobKey(KEY_NAME, KEY_GROUP);

    private static final TriggerKey TRIGGER_KEY = TriggerKey.triggerKey(KEY_NAME, KEY_GROUP);

    private static final JobDetail JOB_DETAIL =
            newJob(StatusRunner.class)
                    .withIdentity(JOB_KEY)
                    .usingJobData(HOST_NAME, Constant.HOST_IP)
                    .build();

    private static final Trigger JOB_TRIGGER =
            TriggerBuilder.newTrigger()
                    .withIdentity(TRIGGER_KEY)
                    .withSchedule(cronSchedule("0/30 * * * * ?"))
                    .startNow()
                    .build();

    @Resource(name = "quartzScheduler")
    Scheduler scheduler;

    @PostConstruct
    public void initQuartzJob() {
        CompletableFuture.runAsync(
                () -> {
                    try {
                        int retry = 10;
                        while (--retry > 0) {
                            if (scheduler.isStarted()) {
                                Trigger trigger = scheduler.getTrigger(TRIGGER_KEY);
                                JobDetail jobDetail = scheduler.getJobDetail(JOB_KEY);
                                if (trigger == null && jobDetail == null) {
                                    scheduler.scheduleJob(JOB_DETAIL, JOB_TRIGGER);
                                } else {
                                    log.warn("Job status monitor already registered.");
                                }
                                return;
                            } else {
                                Thread.sleep(10_000);
                            }
                        }

                        log.error("Retry times of Job status monitor is exhausted.");
                    } catch (Exception e) {
                        log.error("Failed to start job status monitor.", e);
                    }
                });
    }
}
