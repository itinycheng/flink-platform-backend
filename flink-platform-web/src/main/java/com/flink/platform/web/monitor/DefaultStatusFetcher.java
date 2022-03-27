package com.flink.platform.web.monitor;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobRunInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** status monitor. */
@Order()
@Component
public class DefaultStatusFetcher implements StatusFetcher {

    @Autowired private JobRunInfoService jobRunInfoService;

    public boolean isSupported(DeployMode deployMode) {
        return true;
    }

    public StatusInfo getStatus(JobRunInfo jobRunInfo) {
        JobRunInfo current = jobRunInfoService.getById(jobRunInfo.getId());
        return new CustomizeStatusInfo(current.getStatus(), null, null);
    }
}
