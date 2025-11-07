package com.flink.platform.web.entity;

import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.web.quartz.JobFlowRunner;
import jakarta.annotation.Nonnull;
import lombok.Data;
import org.quartz.Job;
import org.quartz.JobKey;
import org.quartz.TriggerKey;

import java.util.HashMap;
import java.util.Map;

/** job flow quartz info. */
@Data
public class JobFlowQuartzInfo implements IQuartzInfo {

    private final JobFlow jobFlow;

    private final Map<String, Object> data = new HashMap<>();

    @Override
    public JobKey getJobKey() {
        return JobKey.jobKey(getName(), getGroup());
    }

    @Override
    public TriggerKey getTriggerKey() {
        return TriggerKey.triggerKey(getName(), getGroup());
    }

    @Override
    public Map<String, Object> getData() {
        return data;
    }

    public JobFlowQuartzInfo addData(@Nonnull String key, @Nonnull Object value) {
        this.data.put(key, value);
        return this;
    }

    private String getName() {
        return jobFlow.getCode();
    }

    private String getGroup() {
        return "JOB_FLOW";
    }

    @Override
    public String getCron() {
        return jobFlow.getCronExpr();
    }

    @Override
    public Class<? extends Job> getJobClass() {
        return JobFlowRunner.class;
    }
}
