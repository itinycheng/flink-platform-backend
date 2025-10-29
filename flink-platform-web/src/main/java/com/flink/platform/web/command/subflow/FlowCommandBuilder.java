package com.flink.platform.web.command.subflow;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.FlowJob;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.JobCommand;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component("flowCommandBuilder")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class FlowCommandBuilder implements CommandBuilder {

    @Override
    public boolean isSupported(JobType jobType, String version) {
        return jobType == JobType.SUB_FLOW;
    }

    @Override
    public JobCommand buildCommand(Long flowRunId, @Nonnull JobRunInfo jobRun) {
        var config = (FlowJob) jobRun.getConfig();
        return new FlowCommand(jobRun.getId(), config.getFlowId());
    }
}
