package com.flink.platform.dao.entity.task;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.flink.platform.common.enums.ExecutionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;

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

        @JsonFormat(pattern = GLOBAL_DATE_TIME_FORMAT, timezone = "GMT+8")
        private LocalDateTime[] timeRange;

        public LocalDateTime getStartTime() {
            if (timeRange == null || timeRange.length != 2) {
                return null;
            }

            return timeRange[0];
        }

        public LocalDateTime getEndTime() {
            if (timeRange == null || timeRange.length != 2) {
                return null;
            }

            return timeRange[1];
        }
    }

    /** dependent relation. */
    public enum DependentRelation {
        AND,
        OR
    }
}
