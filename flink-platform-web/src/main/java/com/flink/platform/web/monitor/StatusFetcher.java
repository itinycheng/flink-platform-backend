package com.flink.platform.web.monitor;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.grpc.JobStatusReply;
import com.flink.platform.grpc.JobStatusRequest;

/** status monitor. */
public interface StatusFetcher {

    boolean isSupported(DeployMode deployMode);

    JobStatusReply getStatus(JobStatusRequest request);
}
