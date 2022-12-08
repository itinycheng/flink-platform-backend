package com.flink.platform.web.service;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.AlertInfo;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.alert.AlertConfig;
import com.flink.platform.dao.entity.alert.FeiShuAlert;
import com.flink.platform.dao.service.AlertService;
import com.flink.platform.dao.service.JobFlowService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;

/** Alert sending service. */
@Slf4j
@Service
public class AlertSendingService {

    @Autowired private AlertService alertService;

    @Autowired private RestTemplate restTemplate;

    @Autowired private JobFlowService jobFlowService;

    public void sendAlerts(JobFlowRun jobFlowRun) {
        List<AlertConfig> alerts = jobFlowRun.getAlerts();
        if (CollectionUtils.isEmpty(alerts)) {
            return;
        }

        ExecutionStatus finalStatus = jobFlowRun.getStatus();
        alerts.stream()
                .filter(
                        alert ->
                                CollectionUtils.isEmpty(alert.getStatuses())
                                        || alert.getStatuses().contains(finalStatus))
                .forEach(alert -> sendAlert(alert.getAlertId(), jobFlowRun));
    }

    public void sendErrAlerts(JobFlow jobFlow) {
        JobFlowRun jobFlowRun = jobFlowService.copyToJobFlowRun(jobFlow);
        jobFlowRun.setStatus(FAILURE);
        sendAlerts(jobFlowRun);
    }

    public boolean sendAlert(Long alertId, JobFlowRun jobFlowRun) {
        AlertInfo alertInfo = alertService.getById(alertId);
        if (alertInfo == null) {
            return false;
        }

        switch (alertInfo.getType()) {
            case FEI_SHU:
                return sendToFeiShu((FeiShuAlert) alertInfo.getConfig(), jobFlowRun);
            case DING_DING:
            case SMS:
            case EMAIL:
            default:
                log.error("Alert type: {} not supported", alertInfo.getType());
                return false;
        }
    }

    public boolean sendToFeiShu(FeiShuAlert alert, JobFlowRun jobFlowRun) {
        try {
            String content =
                    JsonUtil.toJsonString(alert.getContent())
                            .replace("${id}", String.valueOf(jobFlowRun.getId()))
                            .replace("${name}", jobFlowRun.getName())
                            .replace("${status}", jobFlowRun.getStatus().name());
            FeiShuAlert feiShuAlert = new FeiShuAlert(alert.getWebhook(), JsonUtil.toMap(content));
            String message = sendToFeiShu(feiShuAlert);
            log.info(
                    "send notify message to feiShu complete. flowRunId: {}, response: {} ",
                    jobFlowRun.getId(),
                    message);
            return true;
        } catch (Exception e) {
            log.error("send alert info to feiShu failed.", e);
            return false;
        }
    }

    public String sendToFeiShu(FeiShuAlert alert) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForObject(
                alert.getWebhook(), new HttpEntity<>(alert.getContent(), headers), String.class);
    }
}
