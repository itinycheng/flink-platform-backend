package com.flink.platform.web.quartz;

import com.flink.platform.dao.entity.JobFlow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SimpleTrigger;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;

import java.util.HashMap;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises {@link QuartzService} against Quartz's in-memory job store. */
class QuartzServiceSchedulerTest {

    private Scheduler scheduler;

    private QuartzService quartzService;

    @BeforeEach
    void setUp() throws Exception {
        var properties = new Properties();
        properties.setProperty("org.quartz.scheduler.instanceName", "quartz-service-test-" + UUID.randomUUID());
        properties.setProperty("org.quartz.threadPool.threadCount", "1");
        properties.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");

        scheduler = new StdSchedulerFactory(properties).getScheduler();
        scheduler.start();
        scheduler.standby();

        quartzService = new QuartzService();
        quartzService.scheduler = scheduler;
    }

    @AfterEach
    void tearDown() throws Exception {
        if (scheduler != null) {
            scheduler.shutdown(true);
        }
    }

    @Test
    void schedulesAndUnschedulesJobFlow() throws Exception {
        var jobFlow = jobFlow("scheduled-flow");
        var jobKey = JobKey.jobKey(jobFlow.getCode(), "JOB_FLOW");
        var triggerKey = TriggerKey.triggerKey(jobFlow.getCode(), "JOB_FLOW");

        assertTrue(quartzService.scheduleJobFlow(jobFlow));
        assertTrue(scheduler.checkExists(jobKey));
        assertTrue(scheduler.checkExists(triggerKey));
        assertEquals(JobFlowRunner.class, scheduler.getJobDetail(jobKey).getJobClass());

        var trigger = assertInstanceOf(CronTrigger.class, scheduler.getTrigger(triggerKey));
        assertEquals(jobFlow.getCronExpr(), trigger.getCronExpression());
        assertFalse(quartzService.scheduleJobFlow(jobFlow));

        quartzService.unscheduleJobFlow(jobFlow);
        assertFalse(scheduler.checkExists(jobKey));
        assertFalse(scheduler.checkExists(triggerKey));
    }

    @Test
    void storesRunOnceDataInDedicatedJobAndTrigger() throws Exception {
        var jobFlow = jobFlow("run-once-flow");
        var data = new HashMap<String, Object>();
        data.put("flowRunId", 42L);
        data.put("config", "serialized-config");

        assertTrue(quartzService.runFlowOnce(jobFlow, data));

        var jobKey = JobKey.jobKey(jobFlow.getCode(), "JOB_FLOW_RUN_ONCE");
        var triggerKey = TriggerKey.triggerKey(jobFlow.getCode(), "JOB_FLOW_RUN_ONCE");
        var jobDetail = scheduler.getJobDetail(jobKey);

        assertTrue(scheduler.checkExists(jobKey));
        assertEquals(JobFlowRunner.class, jobDetail.getJobClass());
        assertEquals(42L, jobDetail.getJobDataMap().getLong("flowRunId"));
        assertEquals("serialized-config", jobDetail.getJobDataMap().getString("config"));
        assertInstanceOf(SimpleTrigger.class, scheduler.getTrigger(triggerKey));
    }

    private static JobFlow jobFlow(String code) {
        var jobFlow = new JobFlow();
        jobFlow.setCode(code);
        jobFlow.setCronExpr("0 0/10 * * * ?");
        return jobFlow;
    }
}
