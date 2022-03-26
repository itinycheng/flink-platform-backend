package com.flink.platform.web.monitor;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.web.command.JobCallback;
import com.flink.platform.web.external.YarnClientService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.exceptions.ApplicationNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.flink.platform.common.enums.ExecutionStatus.NOT_EXIST;

/** status monitor. */
@Slf4j
@Order(1)
@Component
public class YarnStatusFetcher implements StatusFetcher {

    @Lazy @Autowired private YarnClientService yarnClientService;

    @Override
    public boolean isSupported(DeployMode deployMode) {
        switch (deployMode) {
            case FLINK_YARN_PER:
            case FLINK_YARN_SESSION:
            case FLINK_YARN_RUN_APPLICATION:
                return true;
            default:
                return false;
        }
    }

    @Override
    public StatusInfo getStatus(JobRunInfo jobRunInfo) {
        JobCallback jobCallback = JsonUtil.toBean(jobRunInfo.getBackInfo(), JobCallback.class);
        if (jobCallback == null || StringUtils.isEmpty(jobCallback.getAppId())) {
            return new CustomizeStatusInfo(NOT_EXIST, LocalDateTime.now(), LocalDateTime.now());
        }

        String applicationId = jobCallback.getAppId();
        try {
            ApplicationReport applicationReport =
                    yarnClientService.getApplicationReport(jobCallback.getAppId());
            return new YarnStatusInfo(applicationReport);
        } catch (ApplicationNotFoundException e) {
            log.warn("Application: {} not found.", applicationId, e);
            return new CustomizeStatusInfo(NOT_EXIST, LocalDateTime.now(), LocalDateTime.now());
        } catch (Exception e) {
            log.error(
                    "Use yarn client to get ApplicationReport failed, application: {}",
                    applicationId,
                    e);
            throw new RuntimeException(e);
        }
    }
}
