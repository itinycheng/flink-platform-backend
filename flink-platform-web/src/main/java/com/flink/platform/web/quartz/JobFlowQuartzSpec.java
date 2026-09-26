package com.flink.platform.web.quartz;

import com.flink.platform.dao.entity.JobFlow;
import lombok.Getter;
import org.quartz.Job;

import java.util.Map;

/** Immutable Quartz scheduling specification for a job flow. */
@Getter
final class JobFlowQuartzSpec implements QuartzSpec {

    private static final String GROUP = "JOB_FLOW";

    private final String name;

    private final String cron;

    private final Map<String, Object> data;

    JobFlowQuartzSpec(JobFlow jobFlow) {
        this(jobFlow, Map.of());
    }

    JobFlowQuartzSpec(JobFlow jobFlow, Map<String, Object> data) {
        this.name = jobFlow.getCode();
        this.cron = jobFlow.getCronExpr();
        this.data = Map.copyOf(data);
    }

    @Override
    public String getGroup() {
        return GROUP;
    }

    @Override
    public Class<? extends Job> getJobClass() {
        return JobFlowRunner.class;
    }
}
