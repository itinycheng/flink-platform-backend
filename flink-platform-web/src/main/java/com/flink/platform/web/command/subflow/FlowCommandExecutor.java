package com.flink.platform.web.command.subflow;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.config.AppRunner;
import com.flink.platform.web.entity.JobFlowQuartzInfo;
import com.flink.platform.web.service.QuartzService;
import com.flink.platform.web.util.ThreadUtil;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.flink.platform.common.constants.JobConstant.FLOW_RUN_ID;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.JobFlowType.SUB_FLOW;
import static com.flink.platform.web.util.ThreadUtil.DEFAULT_SLEEP_TIME_MILLIS;

@Slf4j
@Component("flowCommandExecutor")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class FlowCommandExecutor implements CommandExecutor {

    private static final String DEFAULT_NAME = "unnamed";

    private final QuartzService service;

    private final JobFlowService jobFlowService;

    private final JobFlowRunService jobFlowRunService;

    @Override
    public boolean isSupported(JobType jobType) {
        return jobType == JobType.SUB_FLOW;
    }

    @Nonnull
    @Override
    public JobCallback execCommand(@Nonnull JobCommand command) throws Exception {
        var flowId = ((FlowCommand) command).getFlowId();
        var flowRunId = newAndSaveDefault(flowId);
        var jobFlow = jobFlowService.getById(flowId);
        var quartzInfo = new JobFlowQuartzInfo(jobFlow);
        quartzInfo.addData(FLOW_RUN_ID, flowRunId);
        service.runOnce(quartzInfo);

        // wait for flow complete and get status.
        ExecutionStatus status = null;
        while (AppRunner.isRunning()) {
            var jobFlowRun = jobFlowRunService.getById(flowRunId);
            status = jobFlowRun.getStatus();
            if (status.isTerminalState()) {
                break;
            }

            ThreadUtil.sleep(DEFAULT_SLEEP_TIME_MILLIS);
        }

        var callback = new JobCallback();
        callback.setFlowRunId(flowRunId);
        callback.setStatus(status);
        return callback;
    }

    private Long newAndSaveDefault(Long flowId) {
        var jobflowRun = new JobFlowRun();
        jobflowRun.setName(DEFAULT_NAME);
        jobflowRun.setUserId(-1L);
        jobflowRun.setFlowId(flowId);
        jobflowRun.setType(SUB_FLOW);
        jobflowRun.setHost("");
        jobflowRun.setFlow(new JobFlowDag());
        jobflowRun.setStatus(SUBMITTED);
        jobFlowRunService.save(jobflowRun);
        return jobflowRun.getId();
    }
}
