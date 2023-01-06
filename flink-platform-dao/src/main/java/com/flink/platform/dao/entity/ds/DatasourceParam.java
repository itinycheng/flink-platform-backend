package com.flink.platform.dao.entity.ds;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Datasource params. */
@Data
@NoArgsConstructor
public class DatasourceParam {

    private String url;

    private String username;

    private String password;

    private Map<String, String> properties;
}
