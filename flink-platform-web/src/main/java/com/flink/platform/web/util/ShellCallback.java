package com.flink.platform.web.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;

/** Command callback. */
@Data
public class ShellCallback {

    private final boolean exited;

    private final Integer exitCode;

    private final Integer processId;

    @JsonIgnore private String stdMsg;

    @JsonIgnore private String errMsg;

    public ShellCallback(boolean exited, Integer exitCode, Integer processId) {
        this.exited = exited;
        this.exitCode = exitCode;
        this.processId = processId;
    }

    public String getMessage() {
        if (StringUtils.isEmpty(stdMsg) || StringUtils.isEmpty(errMsg)) {
            return StringUtils.isEmpty(stdMsg) ? errMsg : stdMsg;
        }
        return String.join(LINE_SEPARATOR, stdMsg, errMsg);
    }
}
