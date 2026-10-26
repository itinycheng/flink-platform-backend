package com.flink.platform.web.command.subflow;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.util.ExceptionUtil;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.model.FlowRunCreationResult;
import com.flink.platform.web.service.FlowRunTriggerService;
import com.flink.platform.web.service.KillJobService;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;

@Slf4j
@Component("flowCommandExecutor")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class FlowCommandExecutor implements CommandExecutor {

    private final JobFlowService jobFlowService;

    private final JobFlowRunService jobFlowRunService;

    private final JobRunInfoService jobRunService;

    private final FlowRunTriggerService triggerService;

    @Override
    public boolean isSupported(JobType jobType) {
        return jobType == JobType.SUB_FLOW;
    }

    @Nonnull
    @Override
    public JobCallback execCommand(@Nonnull JobCommand command) {
        var flowId = ((FlowCommand) command).getFlowId();
        var subFlow = jobFlowService.getById(flowId);
        if (subFlow == null
                || subFlow.getStatus() == null
                || !subFlow.getStatus().isRunnable()) {
            return new JobCallback("Subflow is not runnable, flowId: " + flowId, FAILURE);
        }

        FlowRunCreationResult result;
        try {
            // Inherit parent flow's scheduleTime so the whole workflow tree shares one biz* anchor.
            var scheduleTime = jobFlowRunService.getScheduleTimeOrNow(command.getFlowRunId());
            result = triggerService.handle(subFlow, scheduleTime);
        } catch (Exception e) {
            log.error("Subflow run failed, flowId: {}", subFlow.getId(), e);
            return new JobCallback(ExceptionUtil.stackTrace(e), FAILURE);
        }

        return switch (result.state()) {
            case CREATED -> {
                var run = result.run();
                var callback = new JobCallback();
                callback.setFlowRunId(run.getId());
                callback.setStatus(run.getStatus());
                yield callback;
            }
            case BLOCKED -> new JobCallback(result.blockMessage(), FAILURE);
        };
    }

    @Override
    public void killCommand(@Nonnull JobCommand command) {
        var jobRun = jobRunService.getById(command.getJobRunId());
        var backInfo = jobRun.getBackInfo();
        if (backInfo == null || backInfo.getFlowRunId() == null) {
            return;
        }

        // avoid circular dependency.
        var service = SpringContext.getBean(KillJobService.class);
        service.killFlowRun(backInfo.getFlowRunId());
    }
}
