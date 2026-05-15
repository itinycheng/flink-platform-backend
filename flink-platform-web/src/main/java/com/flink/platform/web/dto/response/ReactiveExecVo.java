package com.flink.platform.web.dto.response;

import lombok.Getter;

/** async data. */
@Getter
public class ReactiveExecVo extends ReactiveVo {

    public ReactiveExecVo(String execId) {
        super(false, execId);
    }
}
