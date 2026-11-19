package com.flink.platform.web.flowrun;

import com.flink.platform.dao.entity.JobFlowRun;

/** Work to perform after a flow run is inserted and before its admission transaction commits. */
@FunctionalInterface
public interface FlowRunCreationHook {

    /** Read the saved run; throw to roll back admission. Do not mutate the run or perform external I/O. */
    void onCreated(JobFlowRun run);
}
