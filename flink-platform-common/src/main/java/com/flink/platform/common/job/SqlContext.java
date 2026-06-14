package com.flink.platform.common.job;

import com.flink.platform.common.enums.ExecutionMode;
import lombok.Data;

import java.util.List;

/** sql context. */
@Data
public class SqlContext {

    private String id;

    private Long jobId;

    private Long workspaceId;

    private List<Sql> sqls;

    private ExecutionMode execMode;

    private List<Catalog> catalogs;

    /** currently not in use. */
    private List<Function> functions;
}
