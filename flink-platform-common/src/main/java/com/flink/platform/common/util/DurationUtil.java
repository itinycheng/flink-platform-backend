package com.flink.platform.common.util;

import lombok.Getter;

import javax.annotation.Nonnull;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Locale.US;

/** Duration utils, copy from flink. */
public class DurationUtil {

    private static final Map<String, ChronoUnit> LABEL_TO_UNIT_MAP =
            Optional.of(TimeUnit.values())
                    .map(
                            timeUnits -> {
                                Map<String, ChronoUnit> labelToUnit = new HashMap<>();
                                for (TimeUnit timeUnit : timeUnits) {
                                    for (String label : timeUnit.getLabels()) {
                                        labelToUnit.put(label, timeUnit.getUnit());
                                    }
                                }
                                return labelToUnit;
                            })
                    .get();

    public static Duration parse(@Nonnull String text) {
        final String trimmed = text.trim();

        final int len = trimmed.length();
        int pos = 0;

        char current;
        while (pos < len && (current = trimmed.charAt(pos)) >= '0' && current <= '9') {
            pos++;
        }

        final String number = trimmed.substring(0, pos);
        final String unitLabel = trimmed.substring(pos).trim().toLowerCase(US);

        if (number.isEmpty()) {
            throw new NumberFormatException("text does not start with a number");
        }

        final long value;
        try {
            // this throws a NumberFormatException on overflow
            value = Long.parseLong(number);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "The value '"
                            + number
                            + "' cannot be re represented as 64bit number (numeric overflow).");
        }

        if (unitLabel.isEmpty()) {
            return Duration.of(value, ChronoUnit.MILLIS);
        }

        ChronoUnit unit = LABEL_TO_UNIT_MAP.get(unitLabel);
        if (unit != null) {
            return Duration.of(value, unit);
        } else {
            throw new IllegalArgumentException(
                    "Time interval unit label '"
                            + unitLabel
                            + "' does not match any of the recognized units: "
                            + TimeUnit.getAllUnits());
        }
    }

    @Getter
    private enum TimeUnit {
        DAYS(ChronoUnit.DAYS, singular("d"), plural("day")),
        HOURS(ChronoUnit.HOURS, singular("h"), plural("hour")),
        MINUTES(ChronoUnit.MINUTES, singular("min"), singular("m"), plural("minute")),
        SECONDS(ChronoUnit.SECONDS, singular("s"), plural("sec"), plural("second")),
        MILLISECONDS(ChronoUnit.MILLIS, singular("ms"), plural("milli"), plural("millisecond")),
        MICROSECONDS(ChronoUnit.MICROS, singular("µs"), plural("micro"), plural("microsecond")),
        NANOSECONDS(ChronoUnit.NANOS, singular("ns"), plural("nano"), plural("nanosecond"));

        private static final String PLURAL_SUFFIX = "s";

        private final List<String> labels;

        private final ChronoUnit unit;

        TimeUnit(ChronoUnit unit, String[]... labels) {
            this.unit = unit;
            this.labels =
                    Arrays.stream(labels).flatMap(Arrays::stream).collect(Collectors.toList());
        }

        private static String[] singular(String label) {
            return new String[] {label};
        }

        private static String[] plural(String label) {
            return new String[] {label, label + PLURAL_SUFFIX};
        }

        public static String getAllUnits() {
            return Arrays.stream(TimeUnit.values())
                    .map(TimeUnit::createTimeUnitString)
                    .collect(Collectors.joining(", "));
        }

        private static String createTimeUnitString(TimeUnit timeUnit) {
            return timeUnit.name() + ": (" + String.join(" | ", timeUnit.getLabels()) + ")";
        }
    }
}
