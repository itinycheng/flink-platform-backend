package com.flink.platform.web.monitor;

import com.flink.platform.grpc.JobStatusReply;
import jakarta.annotation.Nonnull;

/** status monitor. */
public interface StatusFetcher {

    boolean isSupported(@Nonnull StatusRequest request);

    JobStatusReply getStatus(@Nonnull StatusRequest request);
}
