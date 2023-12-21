package com.flink.platform.web.entity;

import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.web.quartz.JobFlowRunner;
import lombok.Data;
import org.quartz.Job;
import org.quartz.JobKey;
import org.quartz.TriggerKey;

import java.util.HashMap;
import java.util.Map;

import static com.flink.platform.common.constants.JobConstant.CONFIG;

/** job flow quartz info. */
@Data
public class JobFlowQuartzInfo implements IQuartzInfo {

    private final JobFlow jobFlow;

    private ExecutionConfig config;

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
        Map<String, Object> data = new HashMap<>(1);
        if (config != null) {
            data.put(CONFIG, JsonUtil.toJsonString(this.config));
        }
        return data;
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
