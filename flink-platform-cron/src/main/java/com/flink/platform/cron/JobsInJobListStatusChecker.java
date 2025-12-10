package com.flink.platform.cron;

import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Check jobs in the workflow of JOB_LIST type are in normal status. */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class JobsInJobListStatusChecker {

    private static final String ALERT_TEMPLATE = "The latest execution job: %s is %s.";

    private final JobRunInfoService jobRunInfoService;

    private final JobFlowRunService jobFlowRunService;

    private final AlertSendingService alertSendingService;

    @Scheduled(initialDelay = 2 * 60 * 1000, fixedDelay = 10 * 60 * 1000)
    @SchedulerLock(name = "JobsInJobListStatusChecker_checkJobStatus", lockAtMostFor = "PT8M", lockAtLeastFor = "PT5M")
    public void checkJobStatus() {
        var stopWatch = StopWatch.createStarted();
        jobRunInfoService.getJobRunsWithUnexpectedStatus().forEach(jobRun -> {
            String content = ALERT_TEMPLATE.formatted(
                    jobRun.getName(), jobRun.getStatus().name());
            JobFlowRun jobFlowRun = jobFlowRunService.getById(jobRun.getFlowRunId());
            alertSendingService.sendAlerts(jobFlowRun, content);
        });

        stopWatch.stop();
        log.info("Checked jobs in job flows of type JOB_LIST, cost: {} ms", stopWatch.getTime());
    }
}
