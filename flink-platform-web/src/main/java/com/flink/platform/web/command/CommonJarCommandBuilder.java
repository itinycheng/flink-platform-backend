package com.flink.platform.web.command;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.exception.JobCommandGenException;
import com.flink.platform.dao.entity.JobInfo;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/** Common jar command builder. */
@Component("commonJarCommandBuilder")
public class CommonJarCommandBuilder implements CommandBuilder {

    private static final List<JobType> SUPPORTED_JOB_TYPES =
            Collections.singletonList(JobType.COMMON_JAR);

    @Override
    public boolean isSupported(JobType jobType, String version) {
        return SUPPORTED_JOB_TYPES.contains(jobType);
    }

    @Override
    public JobCommand buildCommand(Long flowRunId, JobInfo jobInfo) {
        throw new JobCommandGenException("unsupported job type");
    }
}
