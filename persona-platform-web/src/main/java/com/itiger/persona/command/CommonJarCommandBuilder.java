package com.itiger.persona.command;

import com.itiger.persona.common.exception.FlinkCommandGenException;
import com.itiger.persona.entity.JobInfo;
import com.itiger.persona.enums.JobType;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * @author tiny.wang
 */
@Component("commonJarCommandBuilder")
public class CommonJarCommandBuilder implements JobCommandBuilder {

    private static final List<JobType> SUPPORTED_JOB_TYPES = Collections.singletonList(JobType.COMMON_JAR);

    @Override
    public boolean isSupported(JobType jobType) {
        return SUPPORTED_JOB_TYPES.contains(jobType);
    }

    @Override
    public JobCommand buildCommand(JobInfo jobInfo) {
        throw new FlinkCommandGenException("unsupported job type");
    }
}
