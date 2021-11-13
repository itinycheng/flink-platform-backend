package com.flink.platform.web.monitor;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.dao.entity.JobRunInfo;

/** status monitor. */
public interface StatusFetcher {

    boolean isSupported(DeployMode deployMode);

    StatusInfo getStatus(JobRunInfo jobRunInfo);
}
