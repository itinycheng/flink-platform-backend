package com.flink.platform.web.monitor;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.dao.entity.JobRunInfo;
import org.springframework.stereotype.Component;

/** status monitor. */
@Component
public class DefaultStatusFetcher implements StatusFetcher {

    public boolean isSupported(DeployMode deployMode) {
        return true;
    }

    public StatusInfo getStatus(JobRunInfo jobRunInfo) {
        return null;
    }
}
