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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;

/** Job process grpc service. */
@Slf4j
@GrpcService
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class JobGrpcServer extends JobGrpcServiceGrpc.JobGrpcServiceImplBase {

    private final ProcessJobService processJobService;

    private final ProcessJobStatusService processJobStatusService;

    private final KillJobService killJobService;

    private final FlinkJobService flinkJobService;

    @Override
    public void processJob(ProcessJobRequest request, StreamObserver<ProcessJobReply> responseObserver) {
        long jobRunId = request.getJobRunId();
        try {
            processJobService.processJob(jobRunId);
            ProcessJobReply reply =
                    ProcessJobReply.newBuilder().setJobRunId(jobRunId).build();
            responseObserver.onNext(reply);
        } catch (Exception e) {
            log.error("process job via grpc failed, jobRunId: {}", jobRunId, e);
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
            log.error("get job status via grpc failed, jobRunId: {}", request.getJobRunId(), e);
            responseObserver.onError(buildGrpcException(e));
        }
        responseObserver.onCompleted();
    }

    @Override
    public void killJob(KillJobRequest request, StreamObserver<KillJobReply> responseObserver) {
        long jobRunId = request.getJobRunId();
        try {
            killJobService.killJob(jobRunId);
            KillJobReply reply = KillJobReply.newBuilder().setJobRunId(jobRunId).build();
            responseObserver.onNext(reply);
        } catch (Exception e) {
            log.error("kill job via grpc failed, jobRunId: {}", jobRunId, e);
            responseObserver.onError(buildGrpcException(e));
        }
        responseObserver.onCompleted();
    }

    @Override
    public void savepointJob(SavepointRequest request, StreamObserver<SavepointReply> responseObserver) {
        long jobRunId = request.getJobRunId();
        try {
            flinkJobService.savepoint(jobRunId);
            SavepointReply reply =
                    SavepointReply.newBuilder().setJobRunId(jobRunId).build();
            responseObserver.onNext(reply);
        } catch (Exception e) {
            log.error("flink job savepoint via grpc failed, jobRUnId: {}", jobRunId, e);
            responseObserver.onError(buildGrpcException(e));
        }
        responseObserver.onCompleted();
    }

    private Exception buildGrpcException(Exception e) {
        Status status = e instanceof UnrecoverableException ? Status.UNAVAILABLE : Status.INTERNAL;
        return status.withCause(e).withDescription(ExceptionUtil.stackTrace(e)).asRuntimeException();
    }
}
