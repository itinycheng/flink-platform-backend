package com.flink.platform.cron;

import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.dao.service.WorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.flink.platform.common.enums.WorkerStatus.LEADER;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class UnscheduledJobFlowChecker {

    private static final String ALERT_TEMPLATE =
            "\\nWorkflow's crontab is set but not scheduled: \\nName: %s, Status: %s.";

    private final WorkerService workerService;

    private final JobFlowService jobFlowService;

    private final AlertSendingService alertSendingService;

    @Scheduled(initialDelay = 2 * 60 * 1000, fixedDelay = 60 * 60 * 1000)
    public void checkUnscheduledWorkflow() {
        Worker worker = workerService.getCurWorkerIdAndRole();
        if (worker == null || !LEADER.equals(worker.getRole())) {
            log.info("Current worker is not leader, skip checkUnscheduledWorkflow.");
            return;
        }

        jobFlowService.getUnscheduledJobFlows().forEach(jobFlow -> {
            String content = String.format(
                    ALERT_TEMPLATE, jobFlow.getName(), jobFlow.getStatus().name());
            alertSendingService.sendErrAlerts(jobFlow, content);
        });
    }
}
