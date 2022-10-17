package com.flink.platform.web.command;

import javax.annotation.Nonnull;

import static com.flink.platform.common.enums.ExecutionStatus.KILLABLE;

/** parse result. */
public interface CommandExecutor {

    /**
     * whether support.
     *
     * @param jobCommand job command
     * @return whether support
     */
    boolean isSupported(JobCommand jobCommand);

    @Nonnull
    default JobCallback exec(long jobRunId, JobCommand jobCommand) throws Exception {
        JobCallback jobCallback = execCommand(jobCommand);
        if (jobCallback.getStatus() == KILLABLE) {
            killCommand(jobRunId, jobCallback);
        }
        return jobCallback;
    }

    /**
     * execute command.
     *
     * @param command command to exec
     * @return execute result
     * @throws Exception execute exception
     */
    @Nonnull
    JobCallback execCommand(JobCommand command) throws Exception;

    /**
     * kill command if needed.
     *
     * @param jobRunId job run id
     * @param callback job callback
     */
    default void killCommand(long jobRunId, JobCallback callback) {}
}
