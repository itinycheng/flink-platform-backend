package com.flink.platform.web.monitor;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.grpc.JobStatusRequest;
import lombok.Getter;

/**
 * status request.
 */
@Getter
public class StatusRequest {

    private final JobStatusRequest request;

    private final DeployMode deployMode;

    private final JobType jobType;

    public StatusRequest(JobStatusRequest request) {
        this.request = request;
        this.deployMode = DeployMode.from(request.getDeployMode());
        this.jobType = JobType.from(request.getType());
    }

    public long getJobId() {
        return request.getJobId();
    }

    public long getJobRunId() {
        return request.getJobRunId();
    }
}
