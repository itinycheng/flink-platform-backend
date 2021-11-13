package com.flink.platform.web.external;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Yarn client service. */
@Slf4j
@Lazy
@Component
public class YarnClientService {

    private YarnClient yarnClient;

    @PostConstruct
    public void initYarnClient() {
        yarnClient = YarnClient.createYarnClient();
        yarnClient.init(HadoopUtil.getHadoopConfiguration());
        yarnClient.start();
    }

    public List<ApplicationReport> getApplicationReports(List<String> applicationNames) {
        return applicationNames.stream()
                .map(this::getApplicationReport)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public ApplicationReport getApplicationReport(String applicationName) {
        try {
            ApplicationId applicationId = ApplicationId.fromString(applicationName);
            return yarnClient.getApplicationReport(applicationId);
        } catch (Exception e) {
            log.error("Use yarn client to get ApplicationReport failed.", e);
            return null;
        }
    }

    @PreDestroy
    public void destroy() {
        yarnClient.stop();
    }
}
