package com.flink.platform.web;

import org.junit.Test;
import org.quartz.CronExpression;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

/** Quartz test. */
public class QuartzTest {

    @Test
    public void test() throws ParseException {
        CronExpression cronExpression = new CronExpression("0 43 1 * * ?");
        LocalDateTime of = LocalDateTime.of(2021, 5, 21, 3, 0);
        Date from = Date.from(of.toInstant(ZoneOffset.of("+8")));
        System.out.println(cronExpression.getNextValidTimeAfter(from));
        System.out.println(cronExpression.getNextValidTimeAfter(cronExpression.getNextValidTimeAfter(from)));
    }
}
