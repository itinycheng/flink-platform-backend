package com.flink.platform.web.entity.request;

import com.flink.platform.dao.entity.TagInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

/** Tag request info. */
@Getter
@NoArgsConstructor
public class TagInfoRequest {

    @Delegate
    private final TagInfo tagInfo = new TagInfo();

    public String validateOnCreate() {
        return verifyName();
    }

    public String validateOnUpdate() {
        String msg = verifyId();
        if (msg != null) {
            return msg;
        }

        return verifyName();
    }

    public String verifyId() {
        String errorMsg = null;
        if (getId() == null) {
            errorMsg = "The id of Job flow tag isn't null";
        }
        return errorMsg;
    }

    public String verifyName() {
        String errorMsg = null;
        if (getName() == null) {
            errorMsg = "The name of Job flow tag isn't null";
        }
        return errorMsg;
    }
}
