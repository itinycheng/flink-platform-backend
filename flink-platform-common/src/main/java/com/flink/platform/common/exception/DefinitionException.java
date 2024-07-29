package com.flink.platform.common.exception;

import com.flink.platform.common.enums.ResponseStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** exception. */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class DefinitionException extends RuntimeException {

    private ResponseStatus status;

    public DefinitionException(ResponseStatus responseStatus) {
        this.status = responseStatus;
    }
}
