package com.flink.platform.web.quartz;

import com.flink.platform.dao.entity.JobFlow;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobFlowQuartzSpecTest {

    @Test
    void snapshotsJobFlowAndData() {
        var jobFlow = new JobFlow();
        jobFlow.setCode("flow-code");
        jobFlow.setCronExpr("0 0/10 * * * ?");
        var data = new HashMap<String, Object>();
        data.put("key", "value");

        var spec = new JobFlowQuartzSpec(jobFlow, data);
        jobFlow.setCode("changed");
        data.put("key", "changed");

        assertEquals("flow-code", spec.getName());
        assertEquals("JOB_FLOW", spec.getGroup());
        assertEquals("flow-code", spec.getJobKey().getName());
        assertEquals("JOB_FLOW", spec.getTriggerKey().getGroup());
        assertEquals("value", spec.getData().get("key"));
        assertThrows(UnsupportedOperationException.class, () -> spec.getData().put("another", "value"));
    }
}
