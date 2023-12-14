package com.flink.platform.web;

import com.flink.platform.web.util.ThreadUtil;
import org.junit.Test;
import org.quartz.CronExpression;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    public void testVirtualThreadPool() {
        try (var executorService = (ThreadPoolExecutor) ThreadUtil.newFixedVirtualThreadExecutor("v-thread", 500_000)) {
            AtomicInteger adder = new AtomicInteger(0);
            for (int i = 0; i < 1000_000; i++) {
                executorService.submit(() -> {
                    try {
                        System.out.printf(
                                "name: %s, virtual: %s, active thread num: %s, adder: %s%n",
                                Thread.currentThread().getName(),
                                Thread.currentThread().isVirtual(),
                                0, // inefficient, executorService.getActiveCount(),
                                adder.incrementAndGet());
                        Thread.sleep(3000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }
    }
}
