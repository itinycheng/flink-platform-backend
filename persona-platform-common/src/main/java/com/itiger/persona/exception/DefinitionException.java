package com.itiger.persona.exception;

import com.itiger.persona.enums.ResponseStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author Shik
 * @Title: DefinitionException
 * @ProjectName: datapipeline
 * @Description: TODO
 * @Date: 2021/3/3 下午2:43
 */
@Data
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
