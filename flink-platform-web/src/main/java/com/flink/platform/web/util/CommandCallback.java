package com.flink.platform.web.util;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;

/** Command callback. */
@Data
public class CommandCallback {

    private final boolean success;

    private final String stdMessage;

    private final String errMessage;

    public String getMessage() {
        if (StringUtils.isEmpty(stdMessage) || StringUtils.isEmpty(errMessage)) {
            return StringUtils.isEmpty(stdMessage) ? errMessage : stdMessage;
        }
        return String.join(LINE_SEPARATOR, stdMessage, errMessage);
    }
}
