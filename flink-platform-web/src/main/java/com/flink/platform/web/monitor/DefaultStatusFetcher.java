package com.flink.platform.web.monitor;

import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.grpc.JobStatusReply;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** status monitor. */
@Order()
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DefaultStatusFetcher implements StatusFetcher {

    private final JobRunInfoService jobRunInfoService;

    public boolean isSupported(@Nonnull StatusRequest request) {
        return true;
    }

    public JobStatusReply getStatus(@Nonnull StatusRequest request) {
        var current = jobRunInfoService.getById(request.getJobRunId());
        return JobStatusReply.newBuilder()
                .setStatus(current.getStatus().getCode())
                .build();
    }
}
