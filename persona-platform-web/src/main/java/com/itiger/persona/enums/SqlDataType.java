package com.itiger.persona.enums;

/**
 * @author tiger
 */

public enum SqlDataType {
    /**
     * data type
     */
    INT("", ""),
    LONG("", ""),
    DOUBLE("", ""),
    STRING("'", "'");

    public final String prefix;

    public final String suffix;

    SqlDataType(String prefix, String suffix) {
        this.prefix = prefix;
        this.suffix = suffix;
    }
}
