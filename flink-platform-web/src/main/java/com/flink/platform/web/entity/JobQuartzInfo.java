package com.flink.platform.web.entity;

import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.web.quartz.JobRunner;
import lombok.Data;
import org.quartz.Job;
import org.quartz.JobKey;
import org.quartz.TriggerKey;
import org.quartz.utils.Key;

import java.util.HashMap;
import java.util.Map;

/** job quartz info. */
@Data
public class JobQuartzInfo implements IQuartzInfo {

    private final JobInfo jobInfo;

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
        return new HashMap<>();
    }

    private String getName() {
        return jobInfo.getCode();
    }

    private String getGroup() {
        //"JOB_INFO";
        return Key.DEFAULT_GROUP;
    }

    @Override
    public String getCron() {
        return jobInfo.getCronExpr();
    }

    @Override
    public Class<? extends Job> getJobClass() {
        return JobRunner.class;
    }
}
