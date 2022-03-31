package com.flink.platform.web.service;

import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.AlertInfo;
import com.flink.platform.dao.entity.AlertInfo.FeiShuAlert;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/** Alert sending service. */
@Slf4j
@Service
public class AlertSendingService {

    @Autowired private AlertService alertService;

    @Autowired private RestTemplate restTemplate;

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

    private boolean sendToFeiShu(FeiShuAlert alert, JobFlowRun jobFlowRun) {
        try {
            String content =
                    alert.getContent()
                            .replace("${id}", jobFlowRun.getId().toString())
                            .replace("${name}", jobFlowRun.getName());
            Map<String, Object> data = JsonUtil.toMap(content);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String message =
                    restTemplate.postForObject(
                            alert.getWebhook(), new HttpEntity<>(data, headers), String.class);
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
}
