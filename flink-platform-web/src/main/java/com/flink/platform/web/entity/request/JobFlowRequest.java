package com.flink.platform.web.entity.request;

import com.flink.platform.dao.entity.JobFlow;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

/** Job flow request info. */
@NoArgsConstructor
public class JobFlowRequest {

    @Getter @Delegate private final JobFlow jobFlow = new JobFlow();

    public String validateOnCreate() {
        String msg = verifyName();
        if (msg != null) {
            return msg;
        }

        msg = verifyUserId();
        if (msg != null) {
            return msg;
        }

        msg = verifyCreateTime();
        if (msg != null) {
            return msg;
        }

        msg = verifyUpdateTime();
        return msg;
    }

    public String validateOnUpdate() {
        String msg = verifyId();
        if (msg != null) {
            return msg;
        }

        msg = verifyCreateTime();
        if (msg != null) {
            return msg;
        }

        msg = verifyUpdateTime();
        return msg;
    }

    public String verifyId() {
        String errorMsg = null;
        if (getId() == null) {
            errorMsg = "The id of Job flow isn't null";
        }
        return errorMsg;
    }

    public String verifyName() {
        String errorMsg = null;
        if (getName() == null) {
            errorMsg = "The name of Job flow isn't null";
        }
        return errorMsg;
    }

    public String verifyUserId() {
        String errorMsg = null;
        if (getUserId() == null) {
            errorMsg = "The create user of job flow isn't null";
        }
        return errorMsg;
    }

    public String verifyCronExpr() {
        String errorMsg = null;
        if (getCronExpr() == null) {
            errorMsg = "The cronExpr of job flow isn't null";
        }
        return errorMsg;
    }

    private String verifyCreateTime() {
        String errorMsg = null;
        if (getCreateTime() != null) {
            errorMsg = "The create time of job flow must be null";
        }
        return errorMsg;
    }

    private String verifyUpdateTime() {
        String errorMsg = null;
        if (getUpdateTime() != null) {
            errorMsg = "The update time of job flow must be null";
        }
        return errorMsg;
    }
}
