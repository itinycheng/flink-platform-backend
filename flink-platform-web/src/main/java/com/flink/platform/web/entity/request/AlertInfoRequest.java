package com.flink.platform.web.entity.request;

import com.flink.platform.common.util.Preconditions;
import com.flink.platform.dao.entity.AlertInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

/** Alert request info. */
@NoArgsConstructor
public class AlertInfoRequest {

    @Getter @Delegate private final AlertInfo alertInfo = new AlertInfo();

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
        return Preconditions.checkNotNull(getId(), "The alert id cannot be null");
    }

    public String verifyName() {
        return Preconditions.checkNotNull(getName(), "The alert name cannot be null");
    }

    public String verifyType() {
        return Preconditions.checkNotNull(getType(), "The alert type cannot be null");
    }

    public String verifyConfig() {
        return Preconditions.checkNotNull(getConfig(), "The alert config cannot be null");
    }
}
