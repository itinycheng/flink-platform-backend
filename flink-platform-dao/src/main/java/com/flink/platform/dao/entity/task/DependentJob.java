package com.flink.platform.dao.entity.task;

import com.flink.platform.common.enums.DependentStrategy;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.util.DurationUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.List;

/** dependent job. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DependentJob extends BaseJob {

    private List<DependentItem> dependentItems;

    private DependentRelation relation;

    /**
     * dependent item. <br>
     * trigger sequence: $prev < $cur <= now < $next;
     * It's easy to figure out $next.
     * $prev is the second element in front of $next.
     */
    @Data
    @NoArgsConstructor
    public static class DependentItem {

        private Long flowId;

        private Long jobId;

        private List<ExecutionStatus> statuses;

        private DependentStrategy strategy;

        private String duration;

        public Duration parseDuration() {
            try {
                return DurationUtil.parse(duration);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    /** dependent relation. */
    public enum DependentRelation {
        AND,
        OR
    }
}
