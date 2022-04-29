package com.flink.platform.web.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** table data. */
@Data
@AllArgsConstructor
public class TableDataVo {

    private String[] meta;

    private List<Object[]> data;
}
