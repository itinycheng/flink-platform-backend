package com.flink.platform.web.entity.request;

import com.flink.platform.dao.entity.Datasource;
import com.flink.platform.dao.entity.ds.DatasourceParam;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

import static com.flink.platform.common.util.Preconditions.requireNotNull;

/** Alert request info. */
@Getter
@NoArgsConstructor
public class DatasourceRequest {

    @Delegate
    private final Datasource datasource = new Datasource();

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
        return requireNotNull(getId(), "The datasource id cannot be null");
    }

    public String nameNotNull() {
        return requireNotNull(getName(), "The datasource name cannot be null");
    }

    public String typeNotNull() {
        return requireNotNull(getType(), "The datasource type cannot be null");
    }

    public String paramFieldsNotNull() {
        DatasourceParam params = getParams();
        String msg = requireNotNull(getParams(), "The params cannot be null");
        if (msg != null) {
            return msg;
        }

        msg = requireNotNull(params.getUrl(), "The params.url cannot be null");
        if (msg != null) {
            return msg;
        }

        return requireNotNull(params.getUsername(), "The params.username cannot be null");
    }
}
