package com.flink.platform.cron;

import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.dao.service.WorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.flink.platform.common.enums.WorkerStatus.LEADER;

/** Check jobs in the workflow of JOB_LIST type are in normal status. */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class JobsInJobListStatusChecker {

    private static final String ALERT_TEMPLATE = "The latest execution job: %s is %s.";

    private final JobRunInfoService jobRunInfoService;

    private final WorkerService workerService;

    private final JobFlowRunService jobFlowRunService;

    private final AlertSendingService alertSendingService;

    @Scheduled(initialDelay = 2 * 60 * 1000, fixedDelay = 10 * 60 * 1000)
    public void checkJobStatus() {
        Worker worker = workerService.getCurWorkerIdAndRole();
        if (worker == null || !LEADER.equals(worker.getRole())) {
            log.info("Current worker is not leader, skip checkJobStatus.");
            return;
        }

        jobRunInfoService.getJobRunsWithUnexpectedStatus().forEach(jobRun -> {
            String content = String.format(
                    ALERT_TEMPLATE, jobRun.getName(), jobRun.getStatus().name());
            JobFlowRun jobFlowRun = jobFlowRunService.getById(jobRun.getFlowRunId());
            alertSendingService.sendAlerts(jobFlowRun, content);
        });
    }
}
