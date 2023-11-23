package com.flink.platform.web.entity.request;

import com.flink.platform.dao.entity.JobParam;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

import static com.flink.platform.common.util.Preconditions.requireNotNull;

/** job param request info. */
@Getter
@NoArgsConstructor
public class JobParamRequest {

    @Delegate
    private final JobParam jobParam = new JobParam();

    public String validateOnCreate() {
        String msg = verifyName();
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
        return verifyId();
    }

    public String verifyId() {
        return requireNotNull(getId(), "The jobParam id cannot be null");
    }

    public String verifyType() {
        return requireNotNull(getType(), "The jobParam type cannot be null");
    }

    public String verifyName() {
        return requireNotNull(getParamName(), "The paramName cannot be null");
    }

    public String verifyValue() {
        return requireNotNull(getParamValue(), "The paramValue cannot be null");
    }
}
