package com.flink.platform.web.command;

import com.flink.platform.common.enums.JobType;

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
     * @param jobType job type
     * @return whether support
     */
    boolean isSupported(JobType jobType);

    @Nonnull
    default JobCallback exec(JobCommand jobCommand) throws Exception {
        long jobRunId = jobCommand.getJobRunId();
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

    default void kill(long jobRunId) {
        JobCommand jobCommand = JOB_RUNNING_MAP.get(jobRunId);
        if (jobCommand == null) {
            jobCommand =
                    new JobCommand(jobRunId) {
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
