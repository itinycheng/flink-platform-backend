package com.flink.platform.web.environment;

import com.flink.platform.web.model.ApplicationStatusReport;
import com.flink.platform.web.util.ThreadUtil;
import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.ArrayUtils.EMPTY_STRING_ARRAY;

/**
 * Yarn client service. <br> 1. Manage the lifecycle of YarnClient and FileSystem. <br> 2. Loads
 * hadoop configuration from local disk to initialize Yarn and HDFS clients. <br>
 */
@Slf4j
@Lazy
@Component("environmentHadoopService")
public class HadoopService {

    private static final int REQUEST_APP_REPORT_BATCH_SIZE = 100;

    private final String primaryClusterIdFilePath;

    // tag -> ApplicationStatusReport.
    @Getter
    private final Map<String, ApplicationStatusReport> runningApplications;

    private final ScheduledExecutorService reportRefreshExecutor;

    private YarnClient yarnClient;

    private FileSystem hdfsClient;

    private boolean isPrimaryCluster = true;

    @Autowired
    public HadoopService(@Qualifier("primaryClusterIdFilePath") String primaryClusterIdFilePath) {
        this.primaryClusterIdFilePath = primaryClusterIdFilePath;
        this.runningApplications = new ConcurrentHashMap<>();
        this.reportRefreshExecutor = ThreadUtil.newDaemonSingleScheduledExecutor("yarn-application-report-refresh");
    }

    @SuppressWarnings("unused")
    @PostConstruct
    public void initHadoopClient() {
        log.info("Init Yarn and FileSystem clients of hadoop corresponding to the current running instance.");
        var conf = HadoopHelper.getHadoopConfiguration();
        yarnClient = YarnClient.createYarnClient();
        yarnClient.init(new YarnConfiguration(conf));
        yarnClient.start();

        try {
            hdfsClient = FileSystem.newInstance(conf);
        } catch (Exception e) {
            throw new RuntimeException("create HDFS FileSystem failed", e);
        }

        try {
            isPrimaryCluster =
                    primaryClusterIdFilePath.contains("hdfs") && hdfsClient.exists(new Path(primaryClusterIdFilePath));
        } catch (Exception e) {
            throw new RuntimeException("check cluster id failed");
        }

        // start the application report refresh thread.
        reportRefreshExecutor.scheduleWithFixedDelay(this::refreshReport, RandomUtils.nextInt(10, 30), 40, SECONDS);
    }

    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 4,
            backoff = @Backoff(delay = 1500, multiplier = 2),
            exceptionExpression = "@appRunnerChecker.shouldRetry(#root)")
    public ApplicationStatusReport getStatusReportWithRetry(String applicationTag) throws Exception {
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
        yarnClient.killApplication(report.getApplicationId());
        // remove cached report.
        runningApplications.remove(applicationTag);
        log.info("Kill application id: {}, tag: {} successfully.", report.getApplicationId(), applicationTag);
    }

    public void copyIfNewHdfsAndFileChanged(String localFile, String hdfsFile) throws IOException {
        if (isPrimaryCluster) {
            return;
        }

        var localPath = new Path(localFile);
        var hdfsPath = new Path(hdfsFile);

        boolean isCopy = true;
        if (hdfsClient.exists(hdfsPath)) {
            var local = FileSystem.getLocal(hdfsClient.getConf());
            var localFileStatus = local.getFileStatus(localPath);
            var hdfsFileStatus = hdfsClient.getFileStatus(hdfsPath);
            isCopy = localFileStatus.getLen() != hdfsFileStatus.getLen()
                    || localFileStatus.getModificationTime() > hdfsFileStatus.getModificationTime();
        }

        if (isCopy) {
            hdfsClient.copyFromLocalFile(false, true, localPath, hdfsPath);
        }
    }

    public void writeToFilePath(String filePath, String content) throws IOException {
        var path = new Path(filePath);
        try (var out = hdfsClient.create(path, true)) {
            out.write(content.getBytes(UTF_8));
        }
    }

    private void refreshReport() {
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
    }

    private List<ApplicationReport> getReportsWithRetry(@Nonnull Set<String> applicationTags) throws Exception {
        var applications = yarnClient.getApplications(null, null, applicationTags);
        if (CollectionUtils.isEmpty(applications)) {
            ThreadUtil.sleep(1500);
            applications = yarnClient.getApplications(null, null, applicationTags);
        }
        return applications;
    }

    @PreDestroy
    public void destroy() {
        try {
            yarnClient.stop();
        } catch (Exception ignored) {
        }

        try {
            hdfsClient.close();
        } catch (Exception ignored) {
        }
    }
}
