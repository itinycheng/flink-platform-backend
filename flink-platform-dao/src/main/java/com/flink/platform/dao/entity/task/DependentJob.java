package com.flink.platform.dao.entity.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.TimeRange;
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

        private TimeRange timeRange;

        private Integer lastN;

        @JsonIgnore
        private LocalDateTime[] timeArr;

        @JsonIgnore
        public LocalDateTime getStartTime() {
            LocalDateTime[] timeArr = getTimeArr();
            if (timeArr == null) {
                return null;
            }

            return timeArr[0];
        }

        @JsonIgnore
        public LocalDateTime getEndTime() {
            LocalDateTime[] timeArr = getTimeArr();
            if (timeArr == null) {
                return null;
            }

            return timeArr[1];
        }

        private LocalDateTime[] getTimeArr() {
            if (timeArr != null) {
                return timeArr;
            }

            if (timeRange == null) {
                return null;
            }

            timeArr = timeRange.getCalculator().apply(lastN);
            return timeArr;
        }
    }

    /** dependent relation. */
    public enum DependentRelation {
        AND,
        OR
    }
}
