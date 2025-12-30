package com.flink.platform.web;

import com.flink.platform.web.util.ThreadUtil;
import org.junit.jupiter.api.Test;
import org.quartz.CronExpression;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Quartz test. */
class QuartzTest {

    @Test
    void test() throws Exception {
        var offset = ZoneOffset.of("+8");
        var cronExpression = new CronExpression("0 43 1 * * ?");
        var day0 = LocalDateTime.of(2021, 5, 21, 3, 0);
        var day1 = LocalDateTime.of(2021, 5, 22, 1, 43);
        var day2 = LocalDateTime.of(2021, 5, 23, 1, 43);

        var from = Date.from(day0.toInstant(offset));
        assertEquals(cronExpression.getNextValidTimeAfter(from), Date.from(day1.toInstant(offset)));
        assertEquals(
                cronExpression.getNextValidTimeAfter(cronExpression.getNextValidTimeAfter(from)),
                Date.from(day2.toInstant(offset)));
    }

    public static void main(String[] args) {
        try (var executorService = ThreadUtil.newFixedVirtualThreadExecutor("v-thread", 500_000)) {
            var adder = new AtomicInteger(0);
            for (var i = 0; i < 1000_000; i++) {
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
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }
}
