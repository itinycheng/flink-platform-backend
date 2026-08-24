package com.flink.platform.alert;

import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.flink.platform.common.constants.Constant.EMPTY;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;

/** Alert sending service. */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AlertSendingService {

    private final AlertSender alertSender;

    private final JobFlowService jobFlowService;

    public void sendAlerts(JobFlowRun jobFlowRun) {
        sendAlerts(jobFlowRun, EMPTY);
    }

    public void sendAlerts(JobFlowRun jobFlowRun, String alertMsg) {
        dispatch(jobFlowRun, alertMsg, false);
    }

    public void sendAlertsDirectly(JobFlowRun jobFlowRun, String alertMsg) {
        dispatch(jobFlowRun, alertMsg, true);
    }

    public void sendErrAlertsDirectly(JobFlow jobFlow, String alertMsg) {
        var jobFlowRun = jobFlowService.copyToJobFlowRun(jobFlow);
        jobFlowRun.setStatus(FAILURE);
        sendAlertsDirectly(jobFlowRun, alertMsg);
    }

    private void dispatch(JobFlowRun jobFlowRun, String alertMsg, boolean bypassFilter) {
        var alerts = jobFlowRun.getAlerts();
        if (CollectionUtils.isEmpty(alerts)) {
            return;
        }

        var finalStatus = jobFlowRun.getStatus();
        alerts.stream()
                .filter(alert -> bypassFilter
                        || CollectionUtils.isEmpty(alert.getStatuses())
                        || alert.getStatuses().contains(finalStatus))
                .forEach(alert -> alertSender.sendAlert(alert.getAlertId(), jobFlowRun, alertMsg));
    }
}
