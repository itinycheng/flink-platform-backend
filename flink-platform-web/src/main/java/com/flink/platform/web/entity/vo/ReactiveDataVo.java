package com.flink.platform.web.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** table data. */
@Data
@AllArgsConstructor
public class ReactiveDataVo {

    private String[] meta;

    private List<Object[]> data;

    private String exception;
}
