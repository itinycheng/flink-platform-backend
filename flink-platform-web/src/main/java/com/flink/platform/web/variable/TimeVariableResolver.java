package com.flink.platform.web.variable;

import com.flink.platform.common.constants.JobConstant;
import com.flink.platform.common.util.DateUtil;
import com.flink.platform.common.util.DurationUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobFlowRunService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.flink.platform.common.constants.JobConstant.TIME_PATTERN;
import static com.flink.platform.common.util.Preconditions.checkNotNull;

/**
 * Time variable resolver. Resolves ${time:format[baseTime±duration]} placeholders. baseTime is one
 * of: cur* (curYear/curMonth/curDay/curHour/curMinute/curSecond/curMillisecond) — wall-clock now()
 * biz* (bizYear/bizMonth/bizDay/bizHour/bizMinute/bizSecond/bizMillisecond) — anchored to
 * JobFlowRun.scheduleTime
 */
@Slf4j
@Order(5)
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class TimeVariableResolver implements VariableResolver {

    private final JobFlowRunService jobFlowRunService;

    @Override
    public Map<String, Object> resolve(JobRunInfo jobRun, String content) {
        var result = new HashMap<String, Object>();
        var matcher = TIME_PATTERN.matcher(content);
        var ctx = new ResolveContext(jobRun);
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
            var destTime = baseTimeUnit.provider.apply(ctx);
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

    final class ResolveContext {

        private final JobRunInfo jobRun;

        private @Nullable LocalDateTime scheduleTime;

        ResolveContext(JobRunInfo jobRun) {
            this.jobRun = jobRun;
        }

        public LocalDateTime scheduleTime() {
            if (scheduleTime != null) {
                return scheduleTime;
            }

            var flowRun = jobFlowRunService.getLiteById(jobRun.getFlowRunId());
            scheduleTime = checkNotNull(
                    flowRun.getScheduleTime(),
                    "biz* time variable requires a schedule-time anchor, but none could be resolved for the job run");
            return scheduleTime;
        }
    }

    @Getter
    enum BaseTimeUnit {
        CUR_YEAR(JobConstant.CUR_YEAR, ctx -> LocalDateTime.of(LocalDate.now(), LocalTime.MIN)
                .withDayOfYear(1)),
        CUR_MONTH(JobConstant.CUR_MONTH, ctx -> LocalDateTime.of(LocalDate.now(), LocalTime.MIN)
                .withDayOfMonth(1)),
        CUR_DAY(JobConstant.CUR_DAY, ctx -> LocalDateTime.of(LocalDate.now(), LocalTime.MIN)),
        CUR_HOUR(JobConstant.CUR_HOUR, ctx -> LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)),
        CUR_MINUTE(JobConstant.CUR_MINUTE, ctx -> LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)),
        CUR_SECOND(JobConstant.CUR_SECOND, ctx -> LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)),
        CUR_MILLISECOND(JobConstant.CUR_MILLISECOND, ctx -> LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)),
        BIZ_YEAR(
                JobConstant.BIZ_YEAR,
                ctx -> ctx.scheduleTime().truncatedTo(ChronoUnit.DAYS).withDayOfYear(1)),
        BIZ_MONTH(
                JobConstant.BIZ_MONTH,
                ctx -> ctx.scheduleTime().truncatedTo(ChronoUnit.DAYS).withDayOfMonth(1)),
        BIZ_DAY(JobConstant.BIZ_DAY, ctx -> ctx.scheduleTime().truncatedTo(ChronoUnit.DAYS)),
        BIZ_HOUR(JobConstant.BIZ_HOUR, ctx -> ctx.scheduleTime().truncatedTo(ChronoUnit.HOURS)),
        BIZ_MINUTE(JobConstant.BIZ_MINUTE, ctx -> ctx.scheduleTime().truncatedTo(ChronoUnit.MINUTES)),
        BIZ_SECOND(JobConstant.BIZ_SECOND, ctx -> ctx.scheduleTime().truncatedTo(ChronoUnit.SECONDS)),
        BIZ_MILLISECOND(JobConstant.BIZ_MILLISECOND, ctx -> ctx.scheduleTime().truncatedTo(ChronoUnit.MILLIS));

        private final String name;

        private final Function<TimeVariableResolver.ResolveContext, LocalDateTime> provider;

        BaseTimeUnit(String name, Function<TimeVariableResolver.ResolveContext, LocalDateTime> provider) {
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
