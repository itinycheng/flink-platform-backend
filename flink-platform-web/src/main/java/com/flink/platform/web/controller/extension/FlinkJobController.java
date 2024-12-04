package com.flink.platform.web.controller.extension;

import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.grpc.JobGrpcServiceGrpc.JobGrpcServiceBlockingStub;
import com.flink.platform.grpc.SavepointReply;
import com.flink.platform.grpc.SavepointRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.grpc.JobGrpcClient;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.flink.platform.common.constants.Constant.FLINK;
import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.common.enums.ResponseStatus.OPERATION_NOT_ALLOWED;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;

/**
 * flink job controller.
 */
@RestController
@RequestMapping("/flink")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class FlinkJobController {

    private final JobRunInfoService jobRunService;

    private final JobGrpcClient jobGrpcClient;

    @GetMapping(value = "/savepoint/{jobRunId}")
    public ResultInfo<Long> savepoint(@PathVariable Long jobRunId) {
        JobRunInfo jobRun = jobRunService.getById(jobRunId);
        if (!FLINK.equals(jobRun.getType().getClassification())) {
            return failure(OPERATION_NOT_ALLOWED, "Only flink job can be savepoint");
        }

        if (!RUNNING.equals(jobRun.getStatus()) && !SUCCESS.equals(jobRun.getStatus())) {
            return failure(OPERATION_NOT_ALLOWED, "Job is not running");
        }

        JobCallback callback = jobRun.getBackInfo();
        if (callback == null || StringUtils.isEmpty(callback.getJobId()) || StringUtils.isEmpty(callback.getAppId())) {
            return failure(OPERATION_NOT_ALLOWED, "AppId or JobId not found");
        }

        JobGrpcServiceBlockingStub jobGrpcService = jobGrpcClient.grpcClient(jobRun.getHost());
        SavepointRequest request =
                SavepointRequest.newBuilder().setJobRunId(jobRunId).build();
        SavepointReply savepoint = jobGrpcService.savepointJob(request);
        return success(savepoint.getJobRunId());
    }
}
