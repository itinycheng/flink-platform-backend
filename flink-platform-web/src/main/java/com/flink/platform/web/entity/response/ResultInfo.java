package com.flink.platform.web.entity.response;

import com.flink.platform.common.enums.ResponseStatus;
import com.flink.platform.common.exception.DefinitionException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * response skeleton
 *
 * @author tiny.wang
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResultInfo {

    private int code;

    private String desc;

    private Object result;

    public static ResultInfo success(Object result) {
        ResponseStatus status = ResponseStatus.SUCCESS;
        ResultInfo resultInfo = new ResultInfo();
        resultInfo.setCode(status.getCode());
        resultInfo.setDesc(status.getDesc());
        resultInfo.setResult(result);
        return resultInfo;
    }

    /**
     * 自定义异常返回的结果
     */
    public static ResultInfo defineError(DefinitionException de) {
        ResultInfo result = new ResultInfo();
        result.setCode(de.getCode());
        result.setDesc(de.getMsg());
        return result;
    }

    /**
     * 其他异常处理方法返回的结果
     */
    public static ResultInfo failure(ResponseStatus responseStatus) {
        ResultInfo result = new ResultInfo();
        result.setDesc(responseStatus.getDesc());
        result.setCode(responseStatus.getCode());
        return result;
    }
}
