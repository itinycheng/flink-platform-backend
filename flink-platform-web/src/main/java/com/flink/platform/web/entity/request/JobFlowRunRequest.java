package com.flink.platform.web.entity.request;

import com.flink.platform.dao.entity.JobFlowRun;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

/** Job flow request info. */
@Getter
@NoArgsConstructor
public class JobFlowRunRequest {

    @Delegate
    private final JobFlowRun jobFlowRun = new JobFlowRun();

    public String validateOnUpdate() {
        return verifyId();
    }

    public String verifyId() {
        String errorMsg = null;
        if (getId() == null) {
            errorMsg = "The id of JobFlowRun is null";
        }
        return errorMsg;
    }
}
