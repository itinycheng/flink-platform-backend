package com.flink.platform.web.command;

import com.flink.platform.common.exception.JobCommandGenException;
import com.flink.platform.web.entity.JobInfo;
import com.flink.platform.web.enums.JobType;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * @author tiny.wang
 */
@Component("commonJarCommandBuilder")
public class CommonJarCommandBuilder implements CommandBuilder {

    private static final List<JobType> SUPPORTED_JOB_TYPES = Collections.singletonList(JobType.COMMON_JAR);

    @Override
    public boolean isSupported(JobType jobType) {
        return SUPPORTED_JOB_TYPES.contains(jobType);
    }

    @Override
    public JobCommand buildCommand(JobInfo jobInfo) {
        throw new JobCommandGenException("unsupported job type");
    }
}
