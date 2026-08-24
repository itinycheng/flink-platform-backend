package com.flink.platform.alert;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.alert.FeiShuAlert;
import com.flink.platform.dao.service.AlertService;
import com.flink.platform.dao.service.JobRunInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Alert sender. */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AlertSender {

    private final AlertService alertService;

    private final JobRunInfoService jobRunInfoService;

    private final RestTemplate restTemplate;

    public void sendAlert(Long alertId, JobFlowRun jobFlowRun, String alertMsg) {
        var alertInfo = alertService.getById(alertId);
        if (alertInfo == null) {
            return;
        }

        switch (alertInfo.getType()) {
            case FEI_SHU -> sendToFeiShu((FeiShuAlert) alertInfo.getConfig(), jobFlowRun, alertMsg);
            default -> log.error("Alert type: {} not supported", alertInfo.getType());
        }
    }

    public void sendToFeiShu(FeiShuAlert alert, JobFlowRun jobFlowRun, String alertMsg) {
        try {
            var content = JsonUtil.toJsonString(alert.getContent())
                    .replace("${id}", String.valueOf(jobFlowRun.getId()))
                    .replace("${name}", jobFlowRun.getName())
                    .replace("${status}", jobFlowRun.getStatus().name())
                    .replace("${alertMsg}", alertMsg);
            if (content.contains("${jobRunDetails}")) {
                content = content.replace("${jobRunDetails}", getJobRunDetails(jobFlowRun.getId()));
            }
            var feiShuAlert = new FeiShuAlert(alert.getWebhook(), JsonUtil.toMap(content));
            var message = sendToFeiShu(feiShuAlert);
            log.info(
                    "send notify message to feiShu complete. flowRunId: {}, response: {} ",
                    jobFlowRun.getId(),
                    message);
        } catch (Exception e) {
            log.error("send alert info to feiShu failed.", e);
        }
    }

    private String getJobRunDetails(Long flowRunId) {
        var jobRuns = jobRunInfoService.list(new QueryWrapper<JobRunInfo>()
                .lambda()
                .select(JobRunInfo::getName, JobRunInfo::getStatus)
                .eq(JobRunInfo::getFlowRunId, flowRunId));

        var buffer = new StringBuilder();
        jobRuns.forEach(
                jobRun -> buffer.append("%-10s".formatted(jobRun.getStatus().name()))
                        .append(" : ")
                        .append(jobRun.getName())
                        .append("\n"));
        return buffer.toString();
    }

    public String sendToFeiShu(FeiShuAlert alert) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForObject(
                alert.getWebhook(), new HttpEntity<>(alert.getContent(), headers), String.class);
    }
}
