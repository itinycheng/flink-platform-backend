package com.flink.platform.cron;

import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.dao.service.JobFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class UnscheduledJobFlowChecker {

    private static final String ALERT_TEMPLATE =
            "\\nWorkflow's crontab is set but not scheduled: \\nName: %s, Status: %s.";

    private final JobFlowService jobFlowService;

    private final AlertSendingService alertSendingService;

    @Scheduled(initialDelay = 2 * 60 * 1000, fixedDelay = 60 * 60 * 1000)
    @SchedulerLock(
            name = "UnscheduledJobFlowChecker_checkUnscheduledWorkflow",
            lockAtMostFor = "PT30M",
            lockAtLeastFor = "PT5M")
    public void checkUnscheduledWorkflow() {
        var stopWatch = StopWatch.createStarted();
        jobFlowService.getUnscheduledJobFlows().forEach(jobFlow -> {
            String content = ALERT_TEMPLATE.formatted(
                    jobFlow.getName(), jobFlow.getStatus().name());
            alertSendingService.sendErrAlerts(jobFlow, content);
        });

        stopWatch.stop();
        log.info("Checked unscheduled workflows, cost: {} ms", stopWatch.getTime());
    }
}
