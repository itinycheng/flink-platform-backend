package com.flink.platform.dao.entity.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Command callback. */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShellCallback {

    private Boolean exited;

    private Integer exitCode;

    private Long processId;

    private String stdMsg;

    private String errMsg;

    public ShellCallback(Boolean exited, Integer exitCode, Long processId) {
        this.exited = exited;
        this.exitCode = exitCode;
        this.processId = processId;
    }
}
