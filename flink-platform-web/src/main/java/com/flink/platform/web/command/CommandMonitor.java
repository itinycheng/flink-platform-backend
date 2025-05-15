package com.flink.platform.web.command;

import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.common.ValueSortedMap;
import com.flink.platform.web.service.KillJobService;
import com.flink.platform.web.util.ThreadUtil;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Command monitor.
 */
@Slf4j
public class CommandMonitor {

    private static final ScheduledExecutorService MONITOR =
            ThreadUtil.newDaemonSingleScheduledExecutor("CommandMonitor");

    private static final ExecutorService CANCELER = ThreadUtil.newVirtualThreadExecutor("CommandCanceler");

    private final ValueSortedMap<Long, JobCommand> runningJobMap;

    private KillJobService service;

    public CommandMonitor(@Nonnull ValueSortedMap<Long, JobCommand> runningJobMap) {
        this.runningJobMap = runningJobMap;
    }

    public CommandMonitor start() {
        MONITOR.scheduleWithFixedDelay(this::killTimeoutJobsPeriodically, 5, 2, SECONDS);
        log.info("CommandMonitor started.");
        return this;
    }

    private void killTimeoutJobsPeriodically() {
        if (runningJobMap.size() == 0) {
            return;
        }

        JobCommand command = runningJobMap.getFirst();
        LocalDateTime expected = command.getExpectedStopTime();
        if (expected == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (expected.isAfter(now)) {
            return;
        }

        if (this.service == null) {
            this.service = SpringContext.waitFor(KillJobService.class);
        }

        CANCELER.submit(() -> {
            final var service = this.service;
            service.killJob(command.getJobRunId());
            runningJobMap.remove(command.getJobRunId());
        });
    }
}
