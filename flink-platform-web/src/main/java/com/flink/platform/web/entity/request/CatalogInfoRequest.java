package com.flink.platform.web.entity.request;

import com.flink.platform.dao.entity.CatalogInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.Delegate;

import java.util.List;

import static com.flink.platform.common.util.Preconditions.requireNotNull;

/** Catalog info request. */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class CatalogInfoRequest {

    @Getter
    @Delegate
    private final CatalogInfo catalogInfo = new CatalogInfo();

    private List<String> catalogIds;

    public String validateOnCreate() {
        String msg = nameNotNull();
        if (msg != null) {
            return msg;
        }

        msg = typeNotNull();
        if (msg != null) {
            return msg;
        }

        return createSqlNotNull();
    }

    public String validateOnUpdate() {
        String msg = idNotNull();
        if (msg != null) {
            return msg;
        }
        return createSqlNotNull();
    }

    public String idNotNull() {
        return requireNotNull(getId(), "The catalog id cannot be null");
    }

    public String nameNotNull() {
        return requireNotNull(getName(), "The catalog name cannot be null");
    }

    public String typeNotNull() {
        return requireNotNull(getType(), "The catalog type cannot be null");
    }

    public String createSqlNotNull() {
        return requireNotNull(getCreateSql(), "The create sql cannot be null");
    }
}
