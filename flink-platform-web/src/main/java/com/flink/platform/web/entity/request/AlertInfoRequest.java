package com.flink.platform.web.entity.request;

import com.flink.platform.dao.entity.AlertInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

import static com.flink.platform.common.util.Preconditions.requireNotNull;

/** Alert request info. */
@Getter
@NoArgsConstructor
public class AlertInfoRequest {

    @Delegate
    private final AlertInfo alertInfo = new AlertInfo();

    public String validateOnCreate() {
        String msg = verifyName();
        if (msg != null) {
            return msg;
        }

        msg = verifyType();
        if (msg != null) {
            return msg;
        }

        return verifyConfig();
    }

    public String validateOnUpdate() {
        return verifyId();
    }

    public String verifyId() {
        return requireNotNull(getId(), "The alert id cannot be null");
    }

    public String verifyName() {
        return requireNotNull(getName(), "The alert name cannot be null");
    }

    public String verifyType() {
        return requireNotNull(getType(), "The alert type cannot be null");
    }

    public String verifyConfig() {
        return requireNotNull(getConfig(), "The alert config cannot be null");
    }
}
