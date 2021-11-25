package com.flink.platform.web.entity;

import com.flink.platform.dao.entity.JobFlowRun;
import lombok.Data;
import org.quartz.Job;
import org.quartz.JobKey;
import org.quartz.TriggerKey;

import java.util.Map;

/** Job flow run quartz info. */
@Data
public class JobFlowRunQuartzInfo implements IQuartzInfo {

    private final JobFlowRun jobFlowRun;

    @Override
    public JobKey getJobKey() {
        return null;
    }

    @Override
    public TriggerKey getTriggerKey() {
        return null;
    }

    @Override
    public Map<String, Object> getData() {
        return null;
    }

    @Override
    public String getCron() {
        return null;
    }

    @Override
    public Class<? extends Job> getJobClass() {
        return null;
    }
}
