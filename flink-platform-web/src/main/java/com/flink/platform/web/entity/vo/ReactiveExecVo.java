package com.flink.platform.web.entity.vo;

import lombok.Getter;

/** async data. */
@Getter
public class ReactiveExecVo extends ReactiveVo {

    public ReactiveExecVo(String execId) {
        super(false, execId);
    }
}
