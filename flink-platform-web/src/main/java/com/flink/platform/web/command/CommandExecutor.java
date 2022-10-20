package com.flink.platform.web.command;

import javax.annotation.Nonnull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.flink.platform.common.enums.ExecutionStatus.KILLABLE;
import static com.flink.platform.common.enums.ExecutionStatus.KILLED;

/** parse result. */
public interface CommandExecutor {

    Map<Long, JobCommand> JOB_RUNNING_MAP = new ConcurrentHashMap<>();

    /**
     * whether support.
     *
     * @param jobCommand job command
     * @return whether support
     */
    boolean isSupported(JobCommand jobCommand);

    @Nonnull
    default JobCallback exec(long jobRunId, JobCommand jobCommand) throws Exception {
        try {
            JOB_RUNNING_MAP.put(jobRunId, jobCommand);
            JobCallback jobCallback = execCommand(jobCommand);
            if (jobCallback.getStatus() == KILLABLE) {
                killCommand(jobCommand);
                jobCallback.setStatus(KILLED);
            }
            return jobCallback;
        } finally {
            JOB_RUNNING_MAP.remove(jobRunId);
        }
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
     * @param command job command
     */
    default void killCommand(JobCommand command) {}
}
