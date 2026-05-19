package com.flink.platform.dao.query;

import com.flink.platform.common.enums.JobFlowStatus;
import com.flink.platform.common.enums.JobFlowType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** query parameters for paging job flow details. */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobFlowPageQuery extends BasePageQuery {

    private Long id;
    private JobFlowType type;
    private String name;
    private String tag;
    private JobFlowStatus status;
    private Long workspaceId;

    public boolean isSortByIdDesc() {
        return "-id".equals(getSort());
    }
}
