package com.flink.platform.web.command.subflow;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.util.DateUtil;
import com.flink.platform.common.util.ExceptionUtil;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.lifecycle.AppRunner;
import com.flink.platform.web.quartz.QuartzService;
import com.flink.platform.web.service.JobFlowRunBizService;
import com.flink.platform.web.service.KillJobService;
import com.flink.platform.web.util.ThreadUtil;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;

import static com.flink.platform.common.constants.JobConstant.CONFIG;
import static com.flink.platform.common.constants.JobConstant.FLOW_RUN_ID;
import static com.flink.platform.common.constants.JobConstant.SCHEDULE_TIME;
import static com.flink.platform.common.enums.ExecutionStatus.CREATED;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.JobFlowType.JOB_FLOW;
import static com.flink.platform.web.util.ThreadUtil.ONE_SECOND_MILLIS;

@Slf4j
@Component("flowCommandExecutor")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class FlowCommandExecutor implements CommandExecutor {

    private static final String DEFAULT_NAME = "unnamed";

    private final QuartzService quartzService;

    private final JobFlowService jobFlowService;

    private final JobFlowRunService jobFlowRunService;

    private final JobFlowRunBizService flowRunBizService;

    private final JobRunInfoService jobRunService;

    @Override
    public boolean isSupported(JobType jobType) {
        return jobType == JobType.SUB_FLOW;
    }

    @Nonnull
    @Override
    public JobCallback execCommand(@Nonnull JobCommand command) {
        var flowId = ((FlowCommand) command).getFlowId();
        var subFlow = jobFlowService.getById(flowId);

        // Inherit parent flow's scheduleTime so the whole workflow tree shares one biz* anchor.
        var scheduleTime = jobFlowRunService.getScheduleTimeOrNow(command.getFlowRunId());
        var executionConfig = new ExecutionConfig();
        executionConfig.mergeFrom(subFlow.getConfig());

        Long flowRunId;
        try {
            // Use the Spring proxy so @Transactional is applied to the public method below.
            var self = SpringContext.getBean(FlowCommandExecutor.class);
            flowRunId = self.runOnceInTransaction(subFlow, executionConfig, scheduleTime);
        } catch (Exception e) {
            log.error("Subflow run once time failed, flowId: {}", subFlow.getId(), e);
            return new JobCallback(ExceptionUtil.stackTrace(e), FAILURE);
        }

        // wait until job flow run is inserted.
        ExecutionStatus status = null;
        while (AppRunner.isRunning()) {
            var jobFlowRun = jobFlowRunService.getLiteById(flowRunId);
            if (jobFlowRun != null && jobFlowRun.getStatus() != null) {
                status = jobFlowRun.getStatus();
                break;
            }
            ThreadUtil.sleep(ONE_SECOND_MILLIS);
        }

        var callback = new JobCallback();
        callback.setFlowRunId(flowRunId);
        callback.setStatus(status);
        return callback;
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

    @Transactional(rollbackFor = Exception.class)
    public Long runOnceInTransaction(JobFlow subFlow, ExecutionConfig config, LocalDateTime scheduleTime) {
        jobFlowService.lockForUpdate(subFlow.getId());
        var conflict = flowRunBizService.findRunConflict(subFlow.getId(), config, scheduleTime);
        if (conflict != null) {
            throw new IllegalStateException(
                    "Flow run rejected: " + conflict.type().getDescription()
                            + ", conflicting flowRunId: "
                            + conflict.activeRun().getId());
        }

        var flowRunId = newAndSaveDefault(subFlow, config, scheduleTime);
        if (flowRunId == null) {
            throw new IllegalStateException("Failed to reserve subflow run: " + subFlow.getId());
        }

        var data = new HashMap<String, Object>();
        data.put(FLOW_RUN_ID, flowRunId);
        data.put(CONFIG, JsonUtil.toJsonString(config));
        data.put(SCHEDULE_TIME, DateUtil.format(scheduleTime));
        quartzService.runFlowOnce(subFlow, data);
        return flowRunId;
    }

    private Long newAndSaveDefault(JobFlow subFlow, ExecutionConfig config, LocalDateTime scheduleTime) {
        var jobFlowRun = new JobFlowRun();
        jobFlowRun.setName(DEFAULT_NAME);
        jobFlowRun.setUserId(subFlow.getUserId());
        jobFlowRun.setWorkspaceId(subFlow.getWorkspaceId());
        jobFlowRun.setFlowId(subFlow.getId());
        jobFlowRun.setType(JOB_FLOW);
        jobFlowRun.setHost("");
        jobFlowRun.setFlow(new JobFlowDag());
        jobFlowRun.setStatus(CREATED);
        jobFlowRun.setConfig(config);
        jobFlowRun.setScheduleTime(scheduleTime);
        jobFlowRunService.save(jobFlowRun);
        return jobFlowRun.getId();
    }
}
