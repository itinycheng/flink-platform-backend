package com.flink.platform.web.command.dependent;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCommand;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.common.enums.JobType.DEPENDENT;

/** dependent executor. */
@Slf4j
@Component("dependentCommandExecutor")
public class DependentCommandExecutor implements CommandExecutor {

    @Override
    public boolean isSupported(JobType jobType) {
        return jobType == DEPENDENT;
    }

    @Nonnull
    @Override
    public JobCallback execCommand(@Nonnull JobCommand command) {
        DependentCommand dependentCommand = (DependentCommand) command;
        return new JobCallback(null, dependentCommand.isSuccess() ? SUCCESS : FAILURE);
    }
}
