package com.flink.platform.web.dto.request;

import com.flink.platform.dao.entity.Resource;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

import static com.flink.platform.common.enums.ResourceType.DIR;
import static com.flink.platform.common.util.Preconditions.requireNotNull;
import static com.flink.platform.common.util.Preconditions.requireState;
import static com.flink.platform.web.dto.request.RequestValidations.requireValidName;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/** Alert request info. */
@Getter
@NoArgsConstructor
public class ResourceRequest {

    @Delegate
    private final Resource resource = new Resource();

    public String validateOnCreate() {
        String msg = verifyName();
        if (msg != null) {
            return msg;
        }

        if (getType() != DIR) {
            msg = fullNameNotBlank();
            if (msg != null) {
                return msg;
            }
        }

        return typeNotNull();
    }

    public String validateOnUpdate() {
        return idNotNull();
    }

    public String idNotNull() {
        return requireNotNull(getId(), "The id cannot be null");
    }

    public String verifyName() {
        return requireValidName(getName(), "resource");
    }

    public String fullNameNotBlank() {
        return requireState(isNotBlank(getFullName()), "The full name cannot be null");
    }

    public String typeNotNull() {
        return requireNotNull(getType(), "The type cannot be null");
    }
}
