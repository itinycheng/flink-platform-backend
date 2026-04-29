package com.flink.platform.dao.entity.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/** Workspace configuration. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkspaceConfig {

    private List<Long> workers;
}
