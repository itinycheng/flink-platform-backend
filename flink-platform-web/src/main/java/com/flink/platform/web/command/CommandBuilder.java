package com.flink.platform.web.command;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.BaseJob;
import jakarta.annotation.Nonnull;

import java.time.Duration;
import java.time.LocalDateTime;

/** Command builder. */
public interface CommandBuilder {

    /**
     * whether support.
     *
     * @param jobType job type
     *
     * @return whether support
     */
    boolean isSupported(JobType jobType, String version);

    /**
     * build a command from JobInfo.
     *
     * @param flowRunId flow run id
     * @param jobRun job run info
     *
     * @return job command
     *
     * @throws Exception IO exception
     */
    JobCommand buildCommand(Long flowRunId, @Nonnull JobRunInfo jobRun) throws Exception;

    /**
     * populate timeout info if exists.
     */
    default void populateTimeout(JobCommand command, JobRunInfo jobRun) {
        BaseJob config = jobRun.getConfig();
        if (config == null) {
            return;
        }

        Duration timeout = jobRun.getConfig().parseTimeout();
        if (timeout != null) {
            LocalDateTime plus = jobRun.getSubmitTime().plus(timeout);
            command.setExpectedStopTime(plus);
            command.setTimeout(timeout);
        }
    }
}
