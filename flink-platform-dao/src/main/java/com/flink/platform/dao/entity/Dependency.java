package com.flink.platform.dao.entity;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.ExpiryPolicy;
import com.flink.platform.common.util.DurationUtil;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One dependency of a job flow.
 *
 * <p>trigger sequence: $prev < $cur <= now < $next; It's easy to figure out $next. $prev is the second element
 * in front of $next.
 */
@Data
@NoArgsConstructor
public class Dependency {

    private Long flowId;

    private List<ExecutionStatus> statuses;

    private ExpiryPolicy expiry;

    private String duration;

    private transient Long latestFlowRunId;
    private transient ExecutionStatus latestStatus;
    private transient LocalDateTime latestCreateTime;
    private transient String flowCronExpr;

    public Duration parseDuration() {
        try {
            return DurationUtil.parse(duration);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** {@code isXxx()} would be serialized into {@code depends_on}. */
    public boolean hasValidDuration() {
        try {
            var parsed = DurationUtil.parse(duration);
            return parsed.isPositive();
        } catch (Exception ignored) {
            return false;
        }
    }
}
