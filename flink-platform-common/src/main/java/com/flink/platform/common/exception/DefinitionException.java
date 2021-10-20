package com.flink.platform.common.exception;

import com.flink.platform.common.enums.ResponseStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** exception. */
@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class DefinitionException extends RuntimeException {

    protected int code;
    protected String msg;

    public DefinitionException(ResponseStatus responseStatus) {
        this.code = responseStatus.getCode();
        this.msg = responseStatus.getDesc();
    }
}
