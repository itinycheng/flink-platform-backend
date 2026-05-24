package com.flink.platform.web.environment;

import com.flink.platform.common.environment.EnvironmentType;
import com.flink.platform.common.util.ExceptionUtil;
import com.flink.platform.environment.EnvironmentRegistry;
import com.flink.platform.web.common.AppRunningRetryPredicate;
import com.flink.platform.web.model.ApplicationStatusReport;
import com.flink.platform.web.util.ThreadUtil;
import com.google.common.collect.Lists;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

import static com.flink.platform.web.util.ThreadUtil.THREE_SECOND_MILLIS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.ArrayUtils.EMPTY_STRING_ARRAY;

/**
 * Tracks YARN application lifecycle: caches statuses, periodically refreshes them, and handles
 * kills. Consumes a {@link YarnClient} from {@link EnvironmentRegistry}.
 */
@Slf4j
@Component
@DependsOn("hadoopEnvironmentBootstrap")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class YarnAppService {

    private static final int REQUEST_APP_REPORT_BATCH_SIZE = 100;

    // tag -> ApplicationStatusReport.
    @Getter
    private final Map<String, ApplicationStatusReport> runningApplications = new ConcurrentHashMap<>();

    private final ScheduledExecutorService reportRefreshExecutor =
            ThreadUtil.newDaemonSingleScheduledExecutor("yarn-application-report-refresh");

    private final EnvironmentRegistry registry;

    private YarnClient yarnClient() {
        return registry.getClient(EnvironmentType.YARN);
    }

    @PostConstruct
    public void startScheduler() {
        if (!registry.hasClient(EnvironmentType.YARN)) {
            log.info("YARN environment not registered; skipping refreshReport scheduler.");
            return;
        }

        // start the application report refresh thread.
        reportRefreshExecutor.scheduleWithFixedDelay(
                () -> ExceptionUtil.runWithErrorLogging(this::refreshReport), RandomUtils.nextInt(10, 30), 40, SECONDS);
    }

    @Retryable(maxRetries = 6, delay = 1500, multiplier = 2, predicate = AppRunningRetryPredicate.class)
    public @Nullable ApplicationStatusReport getStatusReportWithRetry(String applicationTag) throws Exception {
        if (StringUtils.isEmpty(applicationTag)) {
            log.warn("The application tag is empty.");
            return null;
        }

        var statusReport = runningApplications.get(applicationTag);
        if (statusReport == null) {
            var applicationTags = Collections.singleton(applicationTag);
            var applications = getReportsWithRetry(applicationTags);
            if (CollectionUtils.isEmpty(applications)) {
                log.warn("No application found for tag: {}", applicationTag);
                return null;
            }

            var report = applications.getFirst();
            statusReport = new ApplicationStatusReport(report);
            runningApplications.put(applicationTag, statusReport);
        }

        if (statusReport.isTerminalState()) {
            runningApplications.remove(applicationTag);
        }

        return statusReport;
    }

    public void killApplication(String applicationTag) throws Exception {
        var statusReport = getStatusReportWithRetry(applicationTag);
        if (statusReport == null || statusReport.isTerminalState()) {
            log.warn("The application with tag: {} does not exist or is in terminal state.", applicationTag);
            return;
        }

        var report = statusReport.getReport();
        yarnClient().killApplication(report.getApplicationId());
        // remove cached report.
        runningApplications.remove(applicationTag);
        log.info("Kill application id: {}, tag: {} successfully.", report.getApplicationId(), applicationTag);
    }

    private void refreshReport() {
        var stopWatch = StopWatch.createStarted();
        var allTags = this.runningApplications.keySet().toArray(EMPTY_STRING_ARRAY);
        var partitions = Lists.partition(Arrays.asList(allTags), REQUEST_APP_REPORT_BATCH_SIZE);
        for (var partition : partitions) {
            try {
                var partitionTags = new HashSet<>(partition);
                var partitionReports = getReportsWithRetry(partitionTags);
                for (var partitionReport : partitionReports) {
                    var applicationTags = partitionReport.getApplicationTags();
                    if (CollectionUtils.isEmpty(applicationTags)) {
                        continue;
                    }

                    var tag = applicationTags.stream()
                            .filter(partitionTags::contains)
                            .findFirst()
                            .orElse(null);

                    if (tag != null) {
                        runningApplications.put(tag, new ApplicationStatusReport(partitionReport));
                    } else {
                        log.warn("No matched tag found for application: {}", partitionReport);
                    }
                }
            } catch (Exception e) {
                log.error("refresh application report failed", e);
            }
        }

        stopWatch.stop();
        log.info("Refresh {} application reports, cost {} ms", allTags.length, stopWatch.getTime());
    }

    private List<ApplicationReport> getReportsWithRetry(Set<String> applicationTags) throws Exception {
        var client = yarnClient();
        var applications = client.getApplications(null, null, applicationTags);
        if (CollectionUtils.isEmpty(applications)) {
            ThreadUtil.sleep(THREE_SECOND_MILLIS);
            applications = client.getApplications(null, null, applicationTags);
        }
        return applications;
    }
}
