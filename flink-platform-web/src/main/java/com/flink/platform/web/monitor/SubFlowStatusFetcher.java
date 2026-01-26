package com.flink.platform.web.monitor;

import com.flink.platform.common.util.DateUtil;
import com.flink.platform.dao.entity.task.FlowJob;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.grpc.JobStatusReply;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static com.flink.platform.common.enums.ExecutionStatus.EXPECTED_FAILURE;
import static com.flink.platform.common.enums.JobType.SUB_FLOW;

@Slf4j
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
        var jobFlowRun = jobFlowRunService.getById(backInfo.getFlowRunId());
        var status = jobFlowRun.getStatus();
        if (EXPECTED_FAILURE.equals(status) && jobRun.getConfig() instanceof FlowJob config) {
            if (config.getExpectedFailureCorrectedTo() != null) {
                status = config.getExpectedFailureCorrectedTo();
            }
        }

        return JobStatusReply.newBuilder()
                .setStatus(status.getCode())
                .setStartTime(DateUtil.timestamp(jobFlowRun.getCreateTime()))
                .setEndTime(DateUtil.timestamp(jobFlowRun.getEndTime()))
                .build();
    }
}
