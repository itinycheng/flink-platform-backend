package com.flink.platform.web.config;

import com.flink.platform.common.exception.UncaughtException;
import com.flink.platform.web.entity.response.ResultInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import static com.flink.platform.common.enums.ResponseStatus.SERVICE_ERROR;
import static com.flink.platform.web.entity.response.ResultInfo.failure;

/** Global exception handler. */
@Slf4j
@ControllerAdvice
@ResponseBody
public class ApiExceptionHandler {

    @ExceptionHandler(value = Exception.class)
    public ResultInfo<Object> exceptionHandler(Exception e) {
        if (e instanceof UncaughtException) {
            throw (UncaughtException) e;
        }

        log.error("Exception: ", e);
        return failure(SERVICE_ERROR);
    }
}
