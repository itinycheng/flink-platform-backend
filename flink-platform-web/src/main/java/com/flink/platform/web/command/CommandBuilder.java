package com.flink.platform.web.command;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.JobRunInfo;
import jakarta.annotation.Nonnull;

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
     * @param jobRun job run info
     *
     * @return job command
     *
     * @throws Exception IO exception
     */
    JobCommand buildCommand(@Nonnull JobRunInfo jobRun) throws Exception;

    /**
     * populate timeout info if exists.
     */
    default void populateTimeout(JobCommand command, JobRunInfo jobRun) {
        var config = jobRun.getConfig();
        if (config == null) {
            return;
        }

        var timeout = jobRun.getConfig().parseTimeout();
        if (timeout != null) {
            var plus = jobRun.getSubmitTime().plus(timeout);
            command.setExpectedStopTime(plus);
            command.setTimeout(timeout);
        }
    }
}
