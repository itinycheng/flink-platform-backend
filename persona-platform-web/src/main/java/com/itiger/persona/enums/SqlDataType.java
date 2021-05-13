package com.itiger.persona.enums;

import static com.itiger.persona.constants.SqlConstant.EMPTY;
import static com.itiger.persona.constants.SqlConstant.SINGLE_QUOTE;

/**
 * @author tiger
 */

public enum SqlDataType {
    /**
     * data type
     */
    NUMBER(EMPTY),
    STRING(SINGLE_QUOTE);

    public final String quote;

    SqlDataType(String quote) {
        this.quote = quote;
    }
}
