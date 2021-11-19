package com.flink.platform.web.entity;

import org.quartz.Job;
import org.quartz.JobKey;
import org.quartz.TriggerKey;

import java.util.Map;

/** quartz info. */
public interface IQuartzInfo {

    JobKey getJobKey();

    TriggerKey getTriggerKey();

    Map<String, Object> getData();

    String getCron();

    Class<? extends Job> getJobClass();
}
