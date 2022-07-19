package com.flink.platform.dao.entity.task;

import com.flink.platform.common.enums.ExecutionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** dependent job. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DependentJob extends BaseJob {

    private List<DependentItem> dependentItems;

    private DependentRelation relation;

    /** dependent item. */
    @Data
    @NoArgsConstructor
    public static class DependentItem {

        private Long flowId;

        private Long jobId;

        private List<ExecutionStatus> statuses;

        private LocalDateTime startTime;

        private LocalDateTime endTime;
    }

    /** dependent relation. */
    public enum DependentRelation {
        AND,
        OR
    }
}
