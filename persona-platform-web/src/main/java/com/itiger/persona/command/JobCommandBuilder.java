package com.itiger.persona.command;

import com.itiger.persona.entity.JobInfo;

/**
 * @author tiny.wang
 */
public interface JobCommandBuilder {

    /**
     * build a command from JobInfo
     *
     * @param jobInfo job info
     * @return shell command string
     */
    String buildCommand(JobInfo jobInfo);
}
