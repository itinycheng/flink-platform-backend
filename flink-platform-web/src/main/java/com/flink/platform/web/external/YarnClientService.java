package com.flink.platform.web.external;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.FinalApplicationStatus;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.exceptions.ApplicationNotFoundException;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

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

    public ApplicationReport getApplicationReport(String applicationName) {
        try {
            ApplicationId applicationId = ApplicationId.fromString(applicationName);
            return yarnClient.getApplicationReport(applicationId);
        } catch (ApplicationNotFoundException e) {
            log.warn("Application: {} not found.", applicationName, e);
            return ApplicationReport.newInstance(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    0,
                    0,
                    0,
                    FinalApplicationStatus.FAILED,
                    null,
                    null,
                    0,
                    null,
                    null);
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
