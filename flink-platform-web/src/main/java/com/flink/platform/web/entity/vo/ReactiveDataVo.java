package com.flink.platform.web.entity.vo;

import lombok.Getter;

import java.util.List;

/** table data. */
@Getter
public class ReactiveDataVo extends ReactiveVo {

    private final String[] meta;

    private final List<Object[]> data;

    private final String exception;

    public ReactiveDataVo(String execId, String[] meta, List<Object[]> data, String exception) {
        super(true, execId);
        this.meta = meta;
        this.data = data;
        this.exception = exception;
    }
}
