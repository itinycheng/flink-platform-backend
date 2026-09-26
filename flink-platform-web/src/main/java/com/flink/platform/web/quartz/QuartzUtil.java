package com.flink.platform.web.quartz;

import com.flink.platform.common.util.DateUtil;
import org.quartz.CronExpression;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

import static com.flink.platform.common.constants.Constant.GLOBAL_ZONE_ID;

/** Stateless Quartz operations. */
public final class QuartzUtil {

    public static Optional<LocalDateTime> nextTriggerTimeAfter(String cronExpr, LocalDateTime sinceTime) {
        try {
            var cronExpression = new CronExpression(cronExpr);
            var sinceDate = Date.from(sinceTime.atZone(GLOBAL_ZONE_ID).toInstant());
            var nextTriggerDate = cronExpression.getNextValidTimeAfter(sinceDate);
            return Optional.ofNullable(nextTriggerDate)
                    .map(date -> date.toInstant().atZone(GLOBAL_ZONE_ID).toLocalDateTime());
        } catch (Exception e) {
            throw new IllegalStateException("Get next valid trigger time failed", e);
        }
    }

    public static boolean isValidExpression(String cronExpr) {
        return CronExpression.isValidExpression(cronExpr);
    }

    public static boolean hasValidScheduleInterval(String cronExpr) throws Exception {
        var cronExpression = new CronExpression(cronExpr);
        var validTime1 = cronExpression.getNextValidTimeAfter(new Date());
        var validTime2 = cronExpression.getNextValidTimeAfter(validTime1);
        return validTime2.getTime() - validTime1.getTime() >= DateUtil.MILLIS_PER_MINUTE;
    }
}
