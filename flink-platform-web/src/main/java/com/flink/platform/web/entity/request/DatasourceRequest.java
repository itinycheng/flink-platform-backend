package com.flink.platform.web.entity.request;

import com.flink.platform.dao.entity.Datasource;
import com.flink.platform.dao.entity.ds.DatasourceParam;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

import static com.flink.platform.common.util.Preconditions.checkNotNull;

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

        return paramFieldsNotNull();
    }

    public String validateOnUpdate() {
        String msg = idNotNull();
        if (msg != null) {
            return msg;
        }
        return paramFieldsNotNull();
    }

    public String idNotNull() {
        return checkNotNull(getId(), "The datasource id cannot be null");
    }

    public String nameNotNull() {
        return checkNotNull(getName(), "The datasource name cannot be null");
    }

    public String typeNotNull() {
        return checkNotNull(getType(), "The datasource type cannot be null");
    }

    public String paramFieldsNotNull() {
        DatasourceParam params = getParams();
        String msg = checkNotNull(getParams(), "The params cannot be null");
        if (msg != null) {
            return msg;
        }

        msg = checkNotNull(params.getUrl(), "The params.url cannot be null");
        if (msg != null) {
            return msg;
        }

        msg = checkNotNull(params.getDriver(), "The params.driver cannot be null");
        if (msg != null) {
            return msg;
        }

        return checkNotNull(params.getUsername(), "The params.username cannot be null");
    }
}
