package com.flink.platform.web.command;

import com.flink.platform.common.util.Preconditions;
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

    private final ScheduledExecutorService monitor;

    private final ExecutorService canceler;

    private final KillJobService service;

    private ValueSortedMap<Long, JobCommand> runningJobMap;

    public CommandMonitor() {
        var service = SpringContext.waitFor(KillJobService.class);
        this.service = Preconditions.checkNotNull(service);
        this.monitor = ThreadUtil.newDaemonSingleScheduledExecutor("CommandMonitor");
        this.canceler = ThreadUtil.newVirtualThreadExecutor("CommandCanceler");
    }

    public CommandMonitor setRunningJobMap(@Nonnull ValueSortedMap<Long, JobCommand> runningJobMap) {
        this.runningJobMap = Preconditions.checkNotNull(runningJobMap);
        return this;
    }

    public CommandMonitor start() {
        monitor.scheduleWithFixedDelay(this::killTimeoutJobsPeriodically, 5, 2, SECONDS);
        log.info("CommandMonitor started.");
        return this;
    }

    private void killTimeoutJobsPeriodically() {
        if (runningJobMap.size() == 0) {
            return;
        }

        var command = runningJobMap.getFirst();
        var expected = command.getExpectedStopTime();
        if (expected == null) {
            return;
        }

        var now = LocalDateTime.now();
        if (expected.isAfter(now)) {
            return;
        }

        canceler.submit(() -> {
            final var runningJobMap = this.runningJobMap;
            service.killJob(command.getJobRunId());
            runningJobMap.remove(command.getJobRunId());
        });
    }
}
