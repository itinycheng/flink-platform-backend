package com.flink.platform.web.entity.vo;

import lombok.Getter;

/** async data. */
@Getter
public class ReactiveExecVo extends ReactiveVo {

    private final String execId;

    public ReactiveExecVo(String execId) {
        super(false);
        this.execId = execId;
    }
}
