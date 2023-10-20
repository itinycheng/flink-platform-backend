package com.flink.platform.dao.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flink.platform.common.enums.TimeoutStrategy;
import com.flink.platform.common.util.DurationUtil;
import jakarta.annotation.Nonnull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Timeout config of Job/JobFlow.
 */
@Slf4j
@Data
public class Timeout {

    private boolean enable;

    private TimeoutStrategy[] strategies;

    private String threshold;

    @JsonIgnore
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private transient Duration thresholdValue;

    public boolean isSatisfied(@Nonnull LocalDateTime startTime) {
        return enable
                && ArrayUtils.isNotEmpty(this.strategies)
                && Duration.between(startTime, LocalDateTime.now())
                        .minus(calcThresholdValue())
                        .isPositive();
    }

    private Duration calcThresholdValue() {
        if (thresholdValue != null) {
            return thresholdValue;
        }

        try {
            thresholdValue = DurationUtil.parse(threshold);
            return thresholdValue;
        } catch (Exception e) {
            log.error("invalid timeout threshold", e);
            return Duration.ZERO;
        }
    }

    public static void main(String[] args) {
        var timeout = new Timeout();
        timeout.setEnable(true);
        timeout.setThreshold("10s");
        timeout.setStrategies(new TimeoutStrategy[] {TimeoutStrategy.FAILURE, TimeoutStrategy.ALARM});

        var startTime = LocalDateTime.now().minus(Duration.ofSeconds(9));
        var bool = timeout.isSatisfied(startTime);
        System.out.println(bool);
    }
}
