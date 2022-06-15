package com.flink.platform.web.grpc;

import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.grpc.JobGrpcServiceGrpc;
import com.flink.platform.grpc.JobStatusReply;
import com.flink.platform.grpc.JobStatusRequest;
import com.flink.platform.grpc.ProcessJobReply;
import com.flink.platform.grpc.ProcessJobRequest;
import com.flink.platform.web.service.ProcessJobService;
import com.flink.platform.web.service.ProcessJobStatusService;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;

/** Job process grpc service. */
@Slf4j
@GrpcService
public class JobProcessGrpcServer extends JobGrpcServiceGrpc.JobGrpcServiceImplBase {

    @Autowired private ProcessJobService processJobService;

    @Autowired private ProcessJobStatusService processJobStatusService;

    @Override
    public void processJob(
            ProcessJobRequest request, StreamObserver<ProcessJobReply> responseObserver) {
        try {
            Long flowRunId = request.getFlowRunId() != 0 ? request.getFlowRunId() : null;
            JobRunInfo jobRunInfo = processJobService.processJob(request.getJobId(), flowRunId);
            ProcessJobReply reply =
                    ProcessJobReply.newBuilder().setJobRunId(jobRunInfo.getId()).build();
            responseObserver.onNext(reply);
        } catch (Exception e) {
            log.error("process job via grpc failed", e);
            responseObserver.onError(e);
        }
        responseObserver.onCompleted();
    }

    @Override
    public void getJobStatus(
            JobStatusRequest request, StreamObserver<JobStatusReply> responseObserver) {
        try {
            JobStatusReply reply = processJobStatusService.getStatus(request);
            responseObserver.onNext(reply);
        } catch (Exception e) {
            log.error("process job via grpc failed", e);
            responseObserver.onError(e);
        }
        responseObserver.onCompleted();
    }
}
