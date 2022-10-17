package com.flink.platform.web.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;

/** Command callback. */
@Data
public class CommandCallback {

    public static final int EXIT_CODE_SUCCESS = 0;

    public static final int EXIT_CODE_FAILURE = 1;

    private final boolean exited;

    private final Integer exitCode;

    private final Integer processId;

    @JsonIgnore private String stdMessage;

    @JsonIgnore private String errMessage;

    public CommandCallback(boolean exited, Integer exitCode, Integer processId) {
        this.exited = exited;
        this.exitCode = exitCode;
        this.processId = processId;
    }

    public String getMessage() {
        if (StringUtils.isEmpty(stdMessage) || StringUtils.isEmpty(errMessage)) {
            return StringUtils.isEmpty(stdMessage) ? errMessage : stdMessage;
        }
        return String.join(LINE_SEPARATOR, stdMessage, errMessage);
    }
}
