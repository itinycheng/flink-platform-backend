package com.itiger.persona.command;

import com.itiger.persona.entity.JobInfo;
import com.itiger.persona.enums.JobType;

/**
 * @author tiny.wang
 */
public interface JobCommandBuilder {

    /**
     * whether support
     *
     * @param jobType job type
     * @return whether support
     */
    boolean isSupported(JobType jobType);

    /**
     * build a command from JobInfo
     *
     * @param jobInfo job info
     * @return shell command
     */
    JobCommand buildCommand(JobInfo jobInfo);

}
