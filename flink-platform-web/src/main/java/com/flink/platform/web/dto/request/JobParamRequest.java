package com.flink.platform.web.dto.request;

import com.flink.platform.dao.entity.JobParam;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

import static com.flink.platform.common.enums.JobParamType.JOB_FLOW;
import static com.flink.platform.common.util.Preconditions.requireNotNull;

/** job param request info. */
@Getter
@NoArgsConstructor
public class JobParamRequest {

    @Delegate
    private final JobParam jobParam = new JobParam();

    public String validateOnCreate() {
        var msg = verifyName();
        if (msg != null) {
            return msg;
        }

        msg = verifyType();
        if (msg != null) {
            return msg;
        }

        return verifyValue();
    }

    public String validateOnUpdate() {
        var msg = verifyType();
        if (msg != null) {
            return msg;
        }

        return verifyId();
    }

    public String verifyId() {
        return requireNotNull(getId(), "The jobParam id cannot be null");
    }

    public String verifyType() {
        return JOB_FLOW.equals(getType())
                ? "Unsupported param type: JOB_FLOW, please set it within the job flow instead"
                : null;
    }

    public String verifyName() {
        return requireNotNull(getParamName(), "The paramName cannot be null");
    }

    public String verifyValue() {
        return requireNotNull(getParamValue(), "The paramValue cannot be null");
    }
}
