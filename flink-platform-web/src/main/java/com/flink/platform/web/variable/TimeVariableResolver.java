package com.flink.platform.web.variable;

import com.flink.platform.common.constants.JobConstant;
import com.flink.platform.common.util.DateUtil;
import com.flink.platform.common.util.DurationUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static com.flink.platform.common.constants.JobConstant.TIME_PATTERN;
import static com.flink.platform.common.util.Preconditions.checkNotNull;

/**
 * Time variable resolver.
 * Resolves ${time:format[baseTime±duration]} placeholders.
 * Example: ${time:yyyyMMdd[curDay-1d]}
 */
@Slf4j
@Order(5)
@Component
public class TimeVariableResolver implements VariableResolver {

    @Override
    public Map<String, Object> resolve(@Nullable JobRunInfo jobRun, String content) {
        var result = new HashMap<String, Object>();
        var matcher = TIME_PATTERN.matcher(content);
        while (matcher.find()) {
            var variable = matcher.group();
            if (result.containsKey(variable)) {
                continue;
            }

            var format = checkNotNull(matcher.group("format"));
            var baseTime = checkNotNull(matcher.group("baseTime"));
            var operator = matcher.group("operator");
            var duration = matcher.group("duration");
            var baseTimeUnit = BaseTimeUnit.of(baseTime);
            var destTime = baseTimeUnit.provider.get();
            // dest time plus duration.
            if (StringUtils.isNotBlank(duration)) {
                var parsedDuration = DurationUtil.parse(duration);
                if ("-".equals(operator)) {
                    parsedDuration = parsedDuration.negated();
                }
                destTime = destTime.plus(parsedDuration);
            }

            result.put(variable, DateUtil.format(destTime, format));
        }
        return result;
    }

    @Getter
    enum BaseTimeUnit {
        CUR_YEAR(JobConstant.CUR_YEAR, () -> LocalDateTime.of(LocalDate.now(), LocalTime.MIN)
                .withDayOfYear(1)),
        CUR_MONTH(JobConstant.CUR_MONTH, () -> LocalDateTime.of(LocalDate.now(), LocalTime.MIN)
                .withDayOfMonth(1)),
        CUR_DAY(JobConstant.CUR_DAY, () -> LocalDateTime.of(LocalDate.now(), LocalTime.MIN)),
        CUR_HOUR(JobConstant.CUR_HOUR, () -> LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)),
        CUR_MINUTE(JobConstant.CUR_MINUTE, () -> LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)),
        CUR_SECOND(JobConstant.CUR_SECOND, () -> LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)),
        CUR_MILLISECOND(JobConstant.CUR_MILLISECOND, () -> LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));

        private final String name;

        private final Supplier<LocalDateTime> provider;

        BaseTimeUnit(String name, Supplier<LocalDateTime> provider) {
            this.name = name;
            this.provider = provider;
        }

        public static BaseTimeUnit of(String name) {
            return Arrays.stream(values())
                    .filter(baseTimeUnit -> baseTimeUnit.name.equalsIgnoreCase(name))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Could not find BaseTimeUnit by name: " + name));
        }
    }
}
