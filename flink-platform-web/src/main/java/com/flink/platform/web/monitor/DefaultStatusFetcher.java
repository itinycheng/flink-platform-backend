package com.flink.platform.web.monitor;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.grpc.JobStatusReply;
import com.flink.platform.grpc.JobStatusRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** status monitor. */
@Order()
@Component
public class DefaultStatusFetcher implements StatusFetcher {

    @Autowired
    private JobRunInfoService jobRunInfoService;

    public boolean isSupported(DeployMode deployMode) {
        return true;
    }

    public JobStatusReply getStatus(JobStatusRequest request) {
        JobRunInfo current = jobRunInfoService.getById(request.getJobRunId());
        return JobStatusReply.newBuilder()
                .setStatus(current.getStatus().getCode())
                .build();
    }
}
