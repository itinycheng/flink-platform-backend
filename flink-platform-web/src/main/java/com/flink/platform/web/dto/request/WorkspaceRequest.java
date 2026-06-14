package com.flink.platform.web.dto.request;

import com.flink.platform.dao.entity.Workspace;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

import static com.flink.platform.common.util.Preconditions.requireNotNull;
import static com.flink.platform.web.dto.request.RequestValidations.requireValidName;

/** Workspace request. */
@Getter
@NoArgsConstructor
public class WorkspaceRequest {

    @Delegate
    private final Workspace workspace = new Workspace();

    public String validateOnCreate() {
        var msg = verifyName();
        if (msg != null) {
            return msg;
        }

        return statusNotNull();
    }

    public String validateOnUpdate() {
        var msg = idNotNull();
        if (msg != null) {
            return msg;
        }

        return verifyName();
    }

    public String idNotNull() {
        return requireNotNull(getId(), "The workspace id cannot be null");
    }

    public String verifyName() {
        return requireValidName(getName(), "workspace");
    }

    public String statusNotNull() {
        return requireNotNull(getStatus(), "The workspace status cannot be null");
    }
}
