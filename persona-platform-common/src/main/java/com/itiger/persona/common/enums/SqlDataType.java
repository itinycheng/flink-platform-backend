package com.itiger.persona.common.enums;

import static com.itiger.persona.common.constants.Constant.EMPTY;
import static com.itiger.persona.common.constants.Constant.SINGLE_QUOTE;

/**
 * @author tiger
 */

public enum SqlDataType {
    /**
     * data type
     */
    NUMBER(EMPTY),
    STRING(SINGLE_QUOTE),
    LIST(EMPTY),
    MAP(EMPTY);

    public final String quote;

    SqlDataType(String quote) {
        this.quote = quote;
    }
}
