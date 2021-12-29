package com.flink.platform.web.entity.response;

import com.flink.platform.common.enums.ResponseStatus;
import com.flink.platform.common.exception.DefinitionException;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response skeleton. */
@Data
@NoArgsConstructor
public class ResultInfo<T> {

    private int code;

    private String desc;

    private T data;

    public static <T> ResultInfo<T> success(T result) {
        ResponseStatus status = ResponseStatus.SUCCESS;
        ResultInfo<T> resultInfo = new ResultInfo<>();
        resultInfo.setCode(status.getCode());
        resultInfo.setDesc(status.getDesc());
        resultInfo.setData(result);
        return resultInfo;
    }

    /** 自定义异常返回的结果. */
    public static <T> ResultInfo<T> defineError(DefinitionException de) {
        ResultInfo<T> result = new ResultInfo<>();
        result.setCode(de.getCode());
        result.setDesc(de.getMsg());
        return result;
    }

    /** 其他异常处理方法返回的结果. */
    public static <T> ResultInfo<T> failure(ResponseStatus responseStatus) {
        ResultInfo<T> result = new ResultInfo<>();
        result.setDesc(responseStatus.getDesc());
        result.setCode(responseStatus.getCode());
        return result;
    }

    public static <T> ResultInfo<T> failure(ResponseStatus responseStatus, String errorMsg) {
        ResultInfo<T> result = new ResultInfo<>();
        result.setCode(responseStatus.getCode());
        result.setDesc(errorMsg);
        return result;
    }
}
