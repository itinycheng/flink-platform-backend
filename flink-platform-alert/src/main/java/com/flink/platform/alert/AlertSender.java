package com.flink.platform.alert;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.AlertInfo;
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

import java.util.List;

/** Alert sender. */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AlertSender {

    private final AlertService alertService;

    private final JobRunInfoService jobRunInfoService;

    private final RestTemplate restTemplate;

    public boolean sendAlert(Long alertId, JobFlowRun jobFlowRun, String alertMsg) {
        AlertInfo alertInfo = alertService.getById(alertId);
        if (alertInfo == null) {
            return false;
        }

        return switch (alertInfo.getType()) {
            case FEI_SHU -> sendToFeiShu((FeiShuAlert) alertInfo.getConfig(), jobFlowRun, alertMsg);
            default -> {
                log.error("Alert type: {} not supported", alertInfo.getType());
                yield false;
            }
        };
    }

    public boolean sendToFeiShu(FeiShuAlert alert, JobFlowRun jobFlowRun, String alertMsg) {
        try {
            String content = JsonUtil.toJsonString(alert.getContent())
                    .replace("${id}", String.valueOf(jobFlowRun.getId()))
                    .replace("${name}", jobFlowRun.getName())
                    .replace("${status}", jobFlowRun.getStatus().name())
                    .replace("${alertMsg}", alertMsg);
            if (content.contains("${jobRunDetails}")) {
                content = content.replace("${jobRunDetails}", getJobRunDetails(jobFlowRun.getId()));
            }
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

    private String getJobRunDetails(Long flowRunId) {
        List<JobRunInfo> jobRuns = jobRunInfoService.list(new QueryWrapper<JobRunInfo>()
                .lambda()
                .select(JobRunInfo::getName, JobRunInfo::getStatus)
                .eq(JobRunInfo::getFlowRunId, flowRunId));

        StringBuilder buffer = new StringBuilder();
        jobRuns.forEach(
                jobRun -> buffer.append("%-10s".formatted(jobRun.getStatus().name()))
                        .append(" : ")
                        .append(jobRun.getName())
                        .append("\n"));
        return buffer.toString();
    }

    public String sendToFeiShu(FeiShuAlert alert) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForObject(
                alert.getWebhook(), new HttpEntity<>(alert.getContent(), headers), String.class);
    }
}
