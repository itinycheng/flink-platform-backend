package com.flink.platform.common.enums;

import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.function.Function;

@Getter
public enum TimeRange {
    LAST_N_HOUR(
            "lastNHour",
            TimeGranularity.HOUR,
            (lastN) -> {
                LocalDate today = LocalDate.now();
                LocalTime nowTime = LocalTime.now();
                return new LocalDateTime[] {
                    LocalDateTime.of(
                            today, LocalTime.of(nowTime.minusHours(lastN).getHour(), 0, 0)),
                    LocalDateTime.of(today, LocalTime.MAX)
                };
            }),

    LAST_N_DAY(
            "lastNDay",
            TimeGranularity.DAY,
            (lastN) -> {
                LocalDate today = LocalDate.now();
                return new LocalDateTime[] {
                    LocalDateTime.of(today.minusDays(lastN), LocalTime.MIN),
                    LocalDateTime.of(today, LocalTime.MAX)
                };
            }),

    LAST_N_WEEK(
            "lastNWeek",
            TimeGranularity.WEEK,
            (lastN) -> {
                LocalDate today = LocalDate.now();
                return new LocalDateTime[] {
                    LocalDateTime.of(
                            today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                    .minusWeeks(lastN),
                            LocalTime.MIN),
                    LocalDateTime.of(
                            today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)),
                            LocalTime.MAX)
                };
            }),

    LAST_N_MONTH(
            "lastNMonth",
            TimeGranularity.MONTH,
            (lastN) -> {
                LocalDate today = LocalDate.now();
                LocalDate monthStart = LocalDate.of(today.getYear(), today.getMonth(), 1);
                return new LocalDateTime[] {
                    LocalDateTime.of(monthStart.minusMonths(lastN), LocalTime.MIN),
                    LocalDateTime.of(today.with(TemporalAdjusters.lastDayOfMonth()), LocalTime.MAX)
                };
            }),
    ;

    private final String name;

    private final TimeGranularity type;

    private final Function<Integer, LocalDateTime[]> calculator;

    TimeRange(String name, TimeGranularity type, Function<Integer, LocalDateTime[]> calculator) {
        this.name = name;
        this.type = type;
        this.calculator = calculator;
    }

    public enum TimeGranularity {
        HOUR,
        DAY,
        WEEK,
        MONTH,
        YEAR
    }
}
