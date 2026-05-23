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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

import static com.flink.platform.web.util.ThreadUtil.THREE_SECOND_MILLIS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.ArrayUtils.EMPTY_STRING_ARRAY;

/**
 * Phase 2 facade — preserves the existing {@code HadoopService} API while delegating client
 * lifetime to {@link EnvironmentRegistry}. {@link YarnClient} and {@link FileSystem} are obtained
 * by name through the registry; this class no longer owns them.
 *
 * <p>The {@code isPrimaryCluster} check is computed lazily on first use to avoid touching HDFS at
 * Spring startup (Phase 3 will harden this further). Phase 7 migrates callers to use the registry
 * directly; Phase 8 deletes this facade entirely.
 */
@Slf4j
@Lazy
@Component("environmentHadoopService")
@DependsOn("hadoopEnvironmentBootstrap")
public class HadoopService {

    private static final int REQUEST_APP_REPORT_BATCH_SIZE = 100;

    // tag -> ApplicationStatusReport.
    @Getter
    private final Map<String, ApplicationStatusReport> runningApplications = new ConcurrentHashMap<>();

    private final ScheduledExecutorService reportRefreshExecutor =
            ThreadUtil.newDaemonSingleScheduledExecutor("yarn-application-report-refresh");

    private final String primaryClusterIdFilePath;

    private final EnvironmentRegistry registry;

    @Nullable
    private volatile Boolean isPrimaryCluster;

    @Autowired
    public HadoopService(
            @Qualifier("primaryClusterIdFilePath") String primaryClusterIdFilePath, EnvironmentRegistry registry) {
        this.primaryClusterIdFilePath = primaryClusterIdFilePath;
        this.registry = registry;
    }

    private YarnClient yarnClient() {
        return registry.find(EnvironmentType.YARN);
    }

    private FileSystem hdfsClient() {
        return registry.find(EnvironmentType.HDFS);
    }

    private synchronized boolean isPrimaryCluster() {
        var cached = isPrimaryCluster;
        if (cached != null) {
            return cached;
        }
        boolean computed;
        try {
            computed = primaryClusterIdFilePath.contains("hdfs")
                    && hdfsClient().exists(new Path(primaryClusterIdFilePath));
        } catch (Exception e) {
            throw new RuntimeException("check cluster id failed", e);
        }
        isPrimaryCluster = computed;
        return computed;
    }

    @PostConstruct
    public void startScheduler() {
        var hasYarn = registry.registered().stream().anyMatch(spec -> spec.getType() == EnvironmentType.YARN);
        if (!hasYarn) {
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

    public void copyIfNewHdfsAndFileChanged(String localFile, String hdfsFile) throws IOException {
        if (isPrimaryCluster()) {
            return;
        }

        var localPath = new Path(localFile);
        var hdfsPath = new Path(hdfsFile);
        var hdfs = hdfsClient();

        boolean isCopy = true;
        if (hdfs.exists(hdfsPath)) {
            var local = FileSystem.getLocal(hdfs.getConf());
            var localFileStatus = local.getFileStatus(localPath);
            var hdfsFileStatus = hdfs.getFileStatus(hdfsPath);
            isCopy = localFileStatus.getLen() != hdfsFileStatus.getLen()
                    || localFileStatus.getModificationTime() > hdfsFileStatus.getModificationTime();
        }

        if (isCopy) {
            hdfs.copyFromLocalFile(false, true, localPath, hdfsPath);
        }
    }

    public void writeToFilePath(String filePath, String content) throws IOException {
        var path = new Path(filePath);
        try (var out = hdfsClient().create(path, true)) {
            out.write(content.getBytes(UTF_8));
        }
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
        var yarn = yarnClient();
        var applications = yarn.getApplications(null, null, applicationTags);
        if (CollectionUtils.isEmpty(applications)) {
            ThreadUtil.sleep(THREE_SECOND_MILLIS);
            applications = yarn.getApplications(null, null, applicationTags);
        }
        return applications;
    }
}
