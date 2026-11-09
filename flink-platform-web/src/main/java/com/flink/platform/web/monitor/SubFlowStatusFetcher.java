package com.flink.platform.web.monitor;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.util.DateUtil;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.grpc.JobStatusReply;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static com.flink.platform.common.enums.JobType.SUB_FLOW;

@Order(1)
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class SubFlowStatusFetcher implements StatusFetcher {

    private final JobRunInfoService jobRunService;

    private final JobFlowRunService jobFlowRunService;

    @Override
    public boolean isSupported(@Nonnull StatusRequest request) {
        return SUB_FLOW.equals(request.getJobType());
    }

    @Override
    public JobStatusReply getStatus(@Nonnull StatusRequest request) {
        var jobRun = jobRunService.getById(request.getJobRunId());
        var backInfo = jobRun.getBackInfo();
        var jobFlowRun = jobFlowRunService.getLiteById(backInfo.getFlowRunId());
        if (jobFlowRun == null) {
            throw new IllegalStateException("Flow run not found: " + backInfo.getFlowRunId());
        }

        var status =
                switch (jobFlowRun.getStatus()) {
                    case CREATED, WAITING -> ExecutionStatus.SUBMITTED;
                    case KILLING -> jobRun.getStatus();
                    case EXPECTED_FAILURE -> ExecutionStatus.FAILURE;
                    default -> jobFlowRun.getStatus();
                };

        return JobStatusReply.newBuilder()
                .setStatus(status.getCode())
                .setStartTime(DateUtil.timestamp(jobFlowRun.getCreateTime()))
                .setEndTime(DateUtil.timestamp(jobFlowRun.getEndTime()))
                .build();
    }
}
