package com.flink.platform.web.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Command callback. */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShellCallback {

    private boolean exited;

    private Integer exitCode;

    private Integer processId;

    private String stdMsg;

    private String errMsg;

    public ShellCallback(boolean exited, Integer exitCode, Integer processId) {
        this.exited = exited;
        this.exitCode = exitCode;
        this.processId = processId;
    }
}
