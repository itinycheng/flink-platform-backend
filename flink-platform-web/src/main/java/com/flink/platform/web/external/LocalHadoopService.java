package com.flink.platform.web.external;

import com.flink.platform.web.model.ApplicationStatusReport;
import com.flink.platform.web.util.ThreadUtil;
import com.google.common.collect.Lists;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

import static org.apache.commons.lang3.ArrayUtils.EMPTY_STRING_ARRAY;

/**
 * Yarn client service. <br> 1. Manage the lifecycle of YarnClient and FileSystem. <br> 2. Loads
 * hadoop configuration from local disk to initialize Yarn and HDFS clients. <br>
 */
@Slf4j
@Lazy
@Component
public class LocalHadoopService {

    private static final int REQUEST_APP_REPORT_BATCH_SIZE = 100;

    private final String clusterIdPath;

    // tag -> ApplicationStatusReport.
    private final Map<String, ApplicationStatusReport> runningApplications;

    private final ScheduledExecutorService reportRefreshExecutor;

    private YarnClient yarnClient;

    private FileSystem hdfsClient;

    private boolean isOnMain = true;

    @Autowired
    public LocalHadoopService(@Qualifier("clusterIdPath") String clusterIdPath) {
        this.clusterIdPath = clusterIdPath;
        this.runningApplications = new ConcurrentHashMap<>();
        this.reportRefreshExecutor = ThreadUtil.newDaemonSingleScheduledExecutor("application-report-refresh");
    }

    @SuppressWarnings("unused")
    @PostConstruct
    public void initHadoopClient() {
        log.info("Init Yarn and FileSystem clients of hadoop corresponding to the current running instance.");
        Configuration conf = HadoopUtil.getHadoopConfiguration();
        yarnClient = YarnClient.createYarnClient();
        yarnClient.init(new YarnConfiguration(conf));
        yarnClient.start();

        try {
            hdfsClient = FileSystem.newInstance(conf);
        } catch (Exception e) {
            throw new RuntimeException("create HDFS FileSystem failed", e);
        }

        try {
            isOnMain = clusterIdPath.contains("hdfs") && hdfsClient.exists(new Path(clusterIdPath));
        } catch (Exception e) {
            throw new RuntimeException("check cluster id failed");
        }

        // start the application report refresh thread.
        reportRefreshExecutor.execute(this::refreshReport);
    }

    public ApplicationStatusReport getApplicationReport(String applicationTag) throws Exception {
        if (StringUtils.isEmpty(applicationTag)) {
            log.warn("The application tag is empty.");
            return null;
        }

        var statusReport = runningApplications.get(applicationTag);
        if (statusReport == null) {
            var applications = yarnClient.getApplications(null, null, Set.of(applicationTag));
            if (CollectionUtils.isEmpty(applications)) {
                log.warn("No application found with tag: {}", applicationTag);
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
        var statusReport = getApplicationReport(applicationTag);
        if (statusReport == null || statusReport.isTerminalState()) {
            log.warn("The application with tag: {} does not exist or is in terminal state.", applicationTag);
            return;
        }

        var report = statusReport.getReport();
        yarnClient.killApplication(report.getApplicationId());
        log.info("Kill application id: {}, tag: {} successfully.", report.getApplicationId(), applicationTag);
    }

    public void copyIfNewHdfsAndFileChanged(String localFile, String hdfsFile) throws IOException {
        if (isOnMain) {
            return;
        }

        Path localPath = new Path(localFile);
        Path hdfsPath = new Path(hdfsFile);

        boolean isCopy = true;
        if (hdfsClient.exists(hdfsPath)) {
            LocalFileSystem local = FileSystem.getLocal(hdfsClient.getConf());
            FileStatus localFileStatus = local.getFileStatus(localPath);
            FileStatus hdfsFileStatus = hdfsClient.getFileStatus(hdfsPath);
            isCopy = localFileStatus.getLen() != hdfsFileStatus.getLen()
                    || localFileStatus.getModificationTime() > hdfsFileStatus.getModificationTime();
        }

        if (isCopy) {
            hdfsClient.copyFromLocalFile(false, true, localPath, hdfsPath);
        }
    }

    private void refreshReport() {
        String[] allTags = this.runningApplications.keySet().toArray(EMPTY_STRING_ARRAY);
        List<List<String>> partitions = Lists.partition(Arrays.asList(allTags), REQUEST_APP_REPORT_BATCH_SIZE);
        for (List<String> partition : partitions) {
            try {
                var partitionTags = new HashSet<>(partition);
                var partitionReports = yarnClient.getApplications(null, null, partitionTags);
                for (var partitionReport : partitionReports) {
                    var applicationTags = partitionReport.getApplicationTags();
                    if (CollectionUtils.isEmpty(applicationTags)) {
                        continue;
                    }

                    String tag = applicationTags.stream()
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
