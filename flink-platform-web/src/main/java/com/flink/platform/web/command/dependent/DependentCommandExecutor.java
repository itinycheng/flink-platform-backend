package com.flink.platform.web.command.dependent;

import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCallback;
import com.flink.platform.web.command.JobCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;

/** dependent executor. */
@Slf4j
@Component("dependentCommandExecutor")
public class DependentCommandExecutor implements CommandExecutor {

    @Override
    public boolean isSupported(JobCommand jobCommand) {
        return jobCommand instanceof DependentCommand;
    }

    @Nonnull
    @Override
    public JobCallback execCommand(JobCommand command) {
        DependentCommand dependentCommand = (DependentCommand) command;
        return new JobCallback(null, dependentCommand.isSuccess() ? SUCCESS : FAILURE);
    }
}
