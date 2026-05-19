package com.flink.platform.dao.query;

import lombok.Data;

/** common pagination and sorting query parameters. */
@Data
public abstract class BasePageQuery {

    private int page = 1;
    private int size = 20;
    private String sort;
}
