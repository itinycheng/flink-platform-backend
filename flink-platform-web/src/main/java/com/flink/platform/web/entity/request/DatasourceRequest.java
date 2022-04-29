package com.flink.platform.web.entity.request;

import com.flink.platform.common.util.Preconditions;
import com.flink.platform.dao.entity.Datasource;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

/** Alert request info. */
@NoArgsConstructor
public class DatasourceRequest {

    @Getter @Delegate private final Datasource datasource = new Datasource();

    public String validateOnCreate() {
        String msg = nameNotNull();
        if (msg != null) {
            return msg;
        }

        msg = typeNotNull();
        if (msg != null) {
            return msg;
        }

        return paramNotNull();
    }

    public String validateOnUpdate() {
        return idNotNull();
    }

    public String idNotNull() {
        return Preconditions.checkNotNull(getId(), "The datasource id cannot be null");
    }

    public String nameNotNull() {
        return Preconditions.checkNotNull(getName(), "The datasource name cannot be null");
    }

    public String typeNotNull() {
        return Preconditions.checkNotNull(getType(), "The datasource type cannot be null");
    }

    public String paramNotNull() {
        return Preconditions.checkNotNull(getParams(), "The datasource params cannot be null");
    }
}
