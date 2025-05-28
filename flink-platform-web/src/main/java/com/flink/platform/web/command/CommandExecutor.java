package com.flink.platform.web.command;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.web.common.ValueSortedMap;
import jakarta.annotation.Nonnull;

import static com.flink.platform.common.enums.ExecutionStatus.KILLABLE;
import static com.flink.platform.common.enums.ExecutionStatus.KILLED;

/** parse result. */
public interface CommandExecutor {

    ValueSortedMap<Long, JobCommand> RUNNING_MAP = new ValueSortedMap<>();

    @SuppressWarnings("unused")
    CommandMonitor MONITOR = new CommandMonitor(RUNNING_MAP).start();

    /**
     * whether support.
     */
    boolean isSupported(JobType jobType);

    @Nonnull
    default JobCallback exec(@Nonnull JobCommand command) throws Exception {
        long jobRunId = command.getJobRunId();
        try {
            RUNNING_MAP.put(jobRunId, command);
            JobCallback callback = execCommand(command);
            if (callback.getStatus() == KILLABLE) {
                killCommand(command);
                callback.setStatus(KILLED);
            }
            return callback;
        } finally {
            RUNNING_MAP.remove(jobRunId);
        }
    }

    default void kill(long jobRunId) {
        JobCommand jobCommand = RUNNING_MAP.get(jobRunId);
        if (jobCommand == null) {
            jobCommand = new JobCommand(jobRunId) {
                @Override
                public String toCommandString() {
                    return "no class matched";
                }
            };
        }
        killCommand(jobCommand);
    }

    /**
     * execute command.
     */
    @Nonnull
    JobCallback execCommand(@Nonnull JobCommand command) throws Exception;

    /**
     * kill command if needed.
     *
     * @param command job command
     */
    default void killCommand(@Nonnull JobCommand command) {}
}
