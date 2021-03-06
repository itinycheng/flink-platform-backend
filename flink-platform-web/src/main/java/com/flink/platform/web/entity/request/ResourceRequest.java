package com.flink.platform.web.entity.request;

import com.flink.platform.common.util.Preconditions;
import com.flink.platform.dao.entity.Resource;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

import java.util.regex.Pattern;

import static com.flink.platform.common.enums.ResourceType.DIR;

/** Alert request info. */
@NoArgsConstructor
public class ResourceRequest {

    private static final String NAME_REGEX = "^[a-zA-Z0-9._-]{5,64}$";

    private static final Pattern NAME_PATTERN = Pattern.compile(NAME_REGEX);

    @Getter @Delegate private final Resource resource = new Resource();

    public String validateOnCreate() {
        String msg = verifyName();
        if (msg != null) {
            return msg;
        }

        if (getType() != DIR) {
            msg = fullNameNotNull();
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
        return Preconditions.checkNotNull(getId(), "The id cannot be null");
    }

    public String verifyName() {
        String errorMsg = null;
        if (getName() == null) {
            errorMsg = "The name of resource cannot be null";
        } else if (!NAME_PATTERN.matcher(getName()).matches()) {
            errorMsg = String.format("invalid job name, regex: `%s`", NAME_REGEX);
        }
        return errorMsg;
    }

    public String fullNameNotNull() {
        return Preconditions.checkNotNull(getFullName(), "The full name cannot be null");
    }

    public String typeNotNull() {
        return Preconditions.checkNotNull(getType(), "The type cannot be null");
    }
}
