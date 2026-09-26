package com.flink.platform.web.quartz;

import org.quartz.Job;
import org.quartz.JobKey;
import org.quartz.TriggerKey;

import java.util.Map;

/** Internal specification used by {@link QuartzService} to create a Quartz job and trigger. */
interface QuartzSpec {

    String getName();

    String getGroup();

    String getCron();

    Map<String, Object> getData();

    Class<? extends Job> getJobClass();

    default JobKey getJobKey() {
        return JobKey.jobKey(getName(), getGroup());
    }

    default TriggerKey getTriggerKey() {
        return TriggerKey.triggerKey(getName(), getGroup());
    }
}
