package com.itiger.persona.comn;

/**
 * @author tiger
 */
public class QuartzException extends RuntimeException {

    public QuartzException(String message) {
        super(message);
    }

    public QuartzException(String message, Throwable cause) {
        super(message, cause);
    }
}
