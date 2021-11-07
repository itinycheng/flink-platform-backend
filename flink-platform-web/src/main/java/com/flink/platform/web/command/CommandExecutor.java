package com.flink.platform.web.command;

import com.flink.platform.common.enums.JobType;

/** parse result. */
public interface CommandExecutor {

    /**
     * whether support.
     *
     * @param jobType job type
     * @return whether support
     */
    boolean isSupported(JobType jobType);

    /**
     * execute command.
     *
     * @param command command to exec
     * @return execute result
     * @throws Exception execute exception
     */
    JobCallback execCommand(String command) throws Exception;
}
