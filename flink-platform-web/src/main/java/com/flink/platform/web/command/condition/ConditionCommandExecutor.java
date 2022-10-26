package com.flink.platform.web.command.condition;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCallback;
import com.flink.platform.web.command.JobCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.common.enums.JobType.CONDITION;

/** condition executor. */
@Slf4j
@Component("conditionCommandExecutor")
public class ConditionCommandExecutor implements CommandExecutor {

    @Override
    public boolean isSupported(JobType jobType) {
        return jobType == CONDITION;
    }

    @Nonnull
    @Override
    public JobCallback execCommand(JobCommand command) {
        ConditionCommand conditionCommand = (ConditionCommand) command;
        return new JobCallback(null, conditionCommand.isSuccess() ? SUCCESS : FAILURE);
    }
}
