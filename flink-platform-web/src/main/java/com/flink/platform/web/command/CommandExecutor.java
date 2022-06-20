package com.flink.platform.web.command;

import javax.annotation.Nonnull;

/** parse result. */
public interface CommandExecutor {

    /**
     * whether support.
     *
     * @param jobCommand job command
     * @return whether support
     */
    boolean isSupported(JobCommand jobCommand);

    /**
     * execute command.
     *
     * @param command command to exec
     * @return execute result
     * @throws Exception execute exception
     */
    @Nonnull
    JobCallback execCommand(JobCommand command) throws Exception;
}
