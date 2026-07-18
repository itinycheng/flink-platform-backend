package com.flink.platform.common.util;

import lombok.Getter;
import lombok.val;

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

    private static final int MAX_HUMANIZE_UNITS = 3;

    private static final Map<String, ChronoUnit> LABEL_TO_UNIT_MAP = Optional.of(TimeUnit.values())
            .map(timeUnits -> {
                Map<String, ChronoUnit> labelToUnit = new HashMap<>();
                for (TimeUnit timeUnit : timeUnits) {
                    for (String label : timeUnit.getLabels()) {
                        labelToUnit.put(label, timeUnit.getUnit());
                    }
                }
                return labelToUnit;
            })
            .get();

    /**
     * Format the given {@link Duration} into a compact human-readable string like "1h 43m 20s".
     */
    public static String humanizeDuration(Duration duration) {
        val totalSeconds = duration.getSeconds();
        val negative = totalSeconds < 0;
        val remaining = Math.abs(totalSeconds);
        val days = remaining / 86400;
        val hours = remaining % 86400 / 3600;
        val minutes = remaining % 3600 / 60;
        val seconds = remaining % 60;

        val values = new long[] {days, hours, minutes, seconds};
        val units = new String[] {"d", "h", "m", "s"};
        val sb = new StringBuilder();
        if (negative) {
            sb.append('-');
        }

        int shown = 0;
        for (int i = 0; i < values.length && shown < MAX_HUMANIZE_UNITS; i++) {
            if (values[i] == 0) {
                continue;
            }
            if (shown > 0) {
                sb.append(' ');
            }
            sb.append(values[i]).append(units[i]);
            shown++;
        }

        return shown == 0 ? "0s" : sb.toString();
    }

    /**
     * Parse the given string to a java {@link Duration}. The string is in format "{length
     * value}{time unit label}", e.g. "123ms", "321 s". If no time unit label is specified, it will
     * be considered as milliseconds.
     *
     * <p>Supported time unit labels are:
     *
     * <ul>
     *   <li>DAYS： "d", "day"
     *   <li>HOURS： "h", "hour"
     *   <li>MINUTES： "m", "min", "minute"
     *   <li>SECONDS： "s", "sec", "second"
     *   <li>MILLISECONDS： "ms", "milli", "millisecond"
     *   <li>MICROSECONDS： "µs", "micro", "microsecond"
     *   <li>NANOSECONDS： "ns", "nano", "nanosecond"
     * </ul>
     *
     * @param text string to parse.
     */
    public static Duration parse(String text) {
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
                    "The value '" + number + "' cannot be re represented as 64bit number (numeric overflow).");
        }

        if (unitLabel.isEmpty()) {
            return Duration.of(value, ChronoUnit.MILLIS);
        }

        ChronoUnit unit = LABEL_TO_UNIT_MAP.get(unitLabel);
        if (unit != null) {
            return Duration.of(value, unit);
        } else {
            throw new IllegalArgumentException("Time interval unit label '"
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
            this.labels = Arrays.stream(labels).flatMap(Arrays::stream).collect(Collectors.toList());
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
