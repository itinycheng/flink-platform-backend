package com.flink.platform.web.quartz;

import com.flink.platform.dao.entity.JobFlow;
import lombok.Getter;
import org.quartz.Job;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
        // remove null values.
        var copied = new HashMap<>(data);
        copied.values().removeIf(Objects::isNull);
        this.data = Map.copyOf(copied);
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
