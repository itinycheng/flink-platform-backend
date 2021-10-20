package com.flink.platform.web.config;

import com.flink.platform.common.enums.ResponseStatus;
import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.web.entity.response.ResultInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/** Global exception handler. */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /** 处理自定义异常. */
    @ExceptionHandler(value = DefinitionException.class)
    @ResponseBody
    public ResultInfo bizExceptionHandler(DefinitionException e) {
        log.error("DefinitionException: ", e);
        return ResultInfo.defineError(e);
    }

    /** 处理其他异常. */
    @ExceptionHandler(value = Exception.class)
    @ResponseBody
    public ResultInfo exceptionHandler(Exception e) {
        log.error("Exception: ", e);
        return ResultInfo.failure(ResponseStatus.SERVICE_ERROR);
    }
}
