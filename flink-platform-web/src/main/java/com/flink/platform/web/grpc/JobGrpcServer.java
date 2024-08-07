package com.flink.platform.web.grpc;

import com.flink.platform.common.exception.UnrecoverableException;
import com.flink.platform.common.util.ExceptionUtil;
import com.flink.platform.grpc.JobGrpcServiceGrpc;
import com.flink.platform.grpc.JobStatusReply;
import com.flink.platform.grpc.JobStatusRequest;
import com.flink.platform.grpc.KillJobReply;
import com.flink.platform.grpc.KillJobRequest;
import com.flink.platform.grpc.ProcessJobReply;
import com.flink.platform.grpc.ProcessJobRequest;
import com.flink.platform.grpc.SavepointReply;
import com.flink.platform.grpc.SavepointRequest;
import com.flink.platform.web.service.FlinkJobService;
import com.flink.platform.web.service.KillJobService;
import com.flink.platform.web.service.ProcessJobService;
import com.flink.platform.web.service.ProcessJobStatusService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;

/** Job process grpc service. */
@Slf4j
@GrpcService
public class JobGrpcServer extends JobGrpcServiceGrpc.JobGrpcServiceImplBase {

    @Autowired
    private ProcessJobService processJobService;

    @Autowired
    private ProcessJobStatusService processJobStatusService;

    @Autowired
    private KillJobService killJobService;

    @Autowired
    private FlinkJobService flinkJobService;

    @Override
    public void processJob(ProcessJobRequest request, StreamObserver<ProcessJobReply> responseObserver) {
        try {
            long jobRunId = request.getJobRunId();
            processJobService.processJob(jobRunId);
            ProcessJobReply reply =
                    ProcessJobReply.newBuilder().setJobRunId(jobRunId).build();
            responseObserver.onNext(reply);
        } catch (Exception e) {
            log.error("process job via grpc failed", e);
            responseObserver.onError(buildGrpcException(e));
        }
        responseObserver.onCompleted();
    }

    @Override
    public void getJobStatus(JobStatusRequest request, StreamObserver<JobStatusReply> responseObserver) {
        try {
            JobStatusReply reply = processJobStatusService.getStatus(request);
            responseObserver.onNext(reply);
        } catch (Exception e) {
            log.error("get job status via grpc failed", e);
            responseObserver.onError(buildGrpcException(e));
        }
        responseObserver.onCompleted();
    }

    @Override
    public void killJob(KillJobRequest request, StreamObserver<KillJobReply> responseObserver) {
        try {
            killJobService.killJob(request.getJobRunId());
            KillJobReply reply =
                    KillJobReply.newBuilder().setJobRunId(request.getJobRunId()).build();
            responseObserver.onNext(reply);
        } catch (Exception e) {
            log.error("kill job via grpc failed", e);
            responseObserver.onError(buildGrpcException(e));
        }
        responseObserver.onCompleted();
    }

    @Override
    public void savepointJob(SavepointRequest request, StreamObserver<SavepointReply> responseObserver) {
        try {
            flinkJobService.savepoint(request.getJobRunId());
            SavepointReply reply = SavepointReply.newBuilder()
                    .setJobRunId(request.getJobRunId())
                    .build();
            responseObserver.onNext(reply);
        } catch (Exception e) {
            log.error("flink job savepoint via grpc failed", e);
            responseObserver.onError(buildGrpcException(e));
        }
        responseObserver.onCompleted();
    }

    private Exception buildGrpcException(Exception e) {
        Status status = e instanceof UnrecoverableException ? Status.UNAVAILABLE : Status.INTERNAL;
        return status.withCause(e).withDescription(ExceptionUtil.stackTrace(e)).asRuntimeException();
    }
}
