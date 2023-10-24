package com.flink.platform.web.service;

import com.flink.platform.alert.AlertSender;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.alert.AlertConfig;
import com.flink.platform.dao.service.JobFlowService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.flink.platform.common.constants.Constant.EMPTY;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;

/** Alert sending service. */
@Slf4j
@Service
public class AlertSendingService {

    @Autowired
    private AlertSender alertSender;

    @Autowired
    private JobFlowService jobFlowService;

    public void sendAlerts(JobFlowRun jobFlowRun) {
        sendAlerts(jobFlowRun, EMPTY);
    }

    public void sendAlerts(JobFlowRun jobFlowRun, String alertMsg) {
        List<AlertConfig> alerts = jobFlowRun.getAlerts();
        if (CollectionUtils.isEmpty(alerts)) {
            return;
        }

        ExecutionStatus finalStatus = jobFlowRun.getStatus();
        alerts.stream()
                .filter(alert -> CollectionUtils.isEmpty(alert.getStatuses())
                        || alert.getStatuses().contains(finalStatus))
                .forEach(alert -> alertSender.sendAlert(alert.getAlertId(), jobFlowRun, alertMsg));
    }

    public void sendErrAlerts(JobFlow jobFlow, String alertMag) {
        JobFlowRun jobFlowRun = jobFlowService.copyToJobFlowRun(jobFlow);
        jobFlowRun.setStatus(FAILURE);
        sendAlerts(jobFlowRun, alertMag);
    }
}
