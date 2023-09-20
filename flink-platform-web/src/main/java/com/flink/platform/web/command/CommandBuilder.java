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
     * @return whether support
     */
    boolean isSupported(JobType jobType, String version);

    /**
     * build a command from JobInfo.
     *
     * @param flowRunId flow run id
     * @param jobRunInfo job run info
     * @return job command
     * @throws Exception IO exception
     */
    JobCommand buildCommand(Long flowRunId, @Nonnull JobRunInfo jobRunInfo) throws Exception;
}
