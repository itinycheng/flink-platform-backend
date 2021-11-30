package com.flink.platform.web.monitor;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.web.command.JobCallback;
import com.flink.platform.web.external.YarnClientService;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** status monitor. */
@Order(1)
@Component
public class YarnStatusFetcher implements StatusFetcher {

    @Autowired private YarnClientService yarnClientService;

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

    public StatusInfo getStatus(JobRunInfo jobRunInfo) {
        JobCallback jobCallback = JsonUtil.toBean(jobRunInfo.getBackInfo(), JobCallback.class);
        if (jobCallback == null || StringUtils.isEmpty(jobCallback.getAppId())) {
            return null;
        }

        ApplicationReport applicationReport =
                yarnClientService.getApplicationReport(jobCallback.getAppId());
        return applicationReport != null ? new YarnStatusInfo(applicationReport) : null;
    }
}
