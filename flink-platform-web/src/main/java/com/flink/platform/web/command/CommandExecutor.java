package com.flink.platform.web.command;

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
    JobCallback execCommand(JobCommand command) throws Exception;
}
