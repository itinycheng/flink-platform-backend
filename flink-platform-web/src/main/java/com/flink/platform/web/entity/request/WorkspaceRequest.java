package com.flink.platform.web.entity.request;

import com.flink.platform.dao.entity.Workspace;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

import static com.flink.platform.common.util.Preconditions.requireNotNull;

/** Workspace request. */
@Getter
@NoArgsConstructor
public class WorkspaceRequest {

    @Delegate
    private final Workspace workspace = new Workspace();

    public String validateOnCreate() {
        return nameNotNull();
    }

    public String validateOnUpdate() {
        return idNotNull();
    }

    public String idNotNull() {
        return requireNotNull(getId(), "The workspace id cannot be null");
    }

    public String nameNotNull() {
        return requireNotNull(getName(), "The workspace name cannot be null");
    }
}
