package com.flink.platform.web.command.flink;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.exception.CommandUnableGenException;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.FlinkJob;
import com.flink.platform.dao.service.ResourceService;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.config.FlinkConfig;
import com.flink.platform.web.environment.HadoopService;
import com.flink.platform.web.service.ResourceManageService;
import com.flink.platform.web.service.StorageService;
import com.flink.platform.web.util.PathUtil;
import com.flink.platform.web.util.YarnHelper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.hadoop.fs.Path;
import org.springframework.context.annotation.Lazy;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.flink.platform.common.constants.Constant.EMPTY;
import static com.flink.platform.common.constants.Constant.SEMICOLON;
import static com.flink.platform.common.constants.Constant.SLASH;
import static com.flink.platform.common.constants.JobConstant.YARN_APPLICATION_NAME;
import static com.flink.platform.common.constants.JobConstant.YARN_APPLICATION_TAG;
import static com.flink.platform.common.constants.JobConstant.YARN_PROVIDED_LIB_DIRS;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/** Flink command builder. */
@Slf4j
public abstract class FlinkCommandBuilder implements CommandBuilder {

    private static final List<JobType> SUPPORTED_JOB_TYPES = Arrays.asList(JobType.FLINK_JAR, JobType.FLINK_SQL);

    private static final String EXEC_MODE = " %s -t %s -d ";

    @Resource(name = "sqlContextHelper")
    protected SqlContextHelper sqlContextHelper;

    @Resource
    protected StorageService storageService;

    @Resource
    protected ResourceService resourceService;

    @Resource
    protected ResourceManageService resourceManageService;

    @Lazy
    @Resource
    protected HadoopService hadoopService;

    protected final FlinkConfig flinkConfig;

    public FlinkCommandBuilder(FlinkConfig flinkConfig) {
        this.flinkConfig = flinkConfig;
    }

    @Override
    public boolean isSupported(JobType jobType, String version) {
        return SUPPORTED_JOB_TYPES.contains(jobType) && flinkConfig.getVersion().equals(version);
    }

    @Override
    public JobCommand buildCommand(Long flowRunId, @Nonnull JobRunInfo jobRun) throws Exception {
        var flinkJob = jobRun.getConfig().unwrap(FlinkJob.class);
        List<String> extJarStoragePaths = getExtJarPaths(flinkJob);

        var deployMode = jobRun.getDeployMode();
        var command = new FlinkCommand(jobRun.getId(), deployMode);
        var execMode = String.format(EXEC_MODE, deployMode.mode, deployMode.target);
        command.setPrefix(flinkConfig.getCommandPath() + execMode);

        // add configurations.
        var configs = command.getConfigs();
        if (flinkJob.getConfigs() != null) {
            configs.putAll(flinkJob.getConfigs());
        }
        // TODO: support more resource providers.
        configs.put(YARN_APPLICATION_NAME, createAppName(jobRun));
        configs.put(YARN_APPLICATION_TAG, createAppTag(jobRun));
        configs.put(YARN_PROVIDED_LIB_DIRS, getMergedLibDirs(extJarStoragePaths));

        command.setClasspaths(downloadAndGetLocalUrls(extJarStoragePaths));
        switch (jobRun.getType()) {
            case FLINK_JAR:
                var localPathOfMainJar = getLocalPathOfMainJar(jobRun);
                command.setMainJar(localPathOfMainJar);
                command.setMainArgs(flinkJob.getMainArgs());
                command.setMainClass(flinkJob.getMainClass());
                command.setOptionArgs(flinkJob.getOptionArgs());
                break;
            case FLINK_SQL:
                var localJarPath = getLocalPathOfSqlJarFile();
                var filePath = sqlContextHelper.convertFromAndSaveToFile(jobRun);
                command.setMainArgs(filePath);
                command.setMainJar(localJarPath);
                command.setMainClass(flinkConfig.getClassName());
                break;
            default:
                throw new CommandUnableGenException("unsupported job type");
        }

        populateTimeout(command, jobRun);
        return command;
    }

    private String createAppTag(JobRunInfo jobRun) {
        return switch (jobRun.getDeployMode()) {
            case FLINK_YARN_PER, FLINK_YARN_SESSION, FLINK_YARN_RUN_APPLICATION -> YarnHelper.getApplicationTag(
                    jobRun.getId());
            default -> EMPTY;
        };
    }

    private String createAppName(JobRunInfo jobRun) {
        var jobName = jobRun.getName().replaceAll("\\s+", "");
        return String.join(
                "-", jobRun.getExecMode().name(), jobRun.getJobCode() + "_" + jobRun.getFlowRunId(), jobName);
    }

    private String getLocalPathOfMainJar(JobRunInfo jobRun) throws IOException {
        var jarPath = jobRun.getSubject();
        if (!jarPath.toLowerCase().startsWith("hdfs")) {
            return jarPath;
        }

        return resourceManageService.copyFromStorageToLocal(jarPath);
    }

    private Object getMergedLibDirs(List<String> extJarList) {
        var userLibDirs = extJarList.stream()
                .map(extJar -> storageService.getParentPath(extJar))
                .distinct()
                .collect(joining(SEMICOLON));
        return !userLibDirs.isEmpty()
                ? String.join(SEMICOLON, flinkConfig.getLibDirs(), userLibDirs)
                : flinkConfig.getLibDirs();
    }

    private List<URL> downloadAndGetLocalUrls(List<String> extJarList) throws Exception {
        var jarUrls = new ArrayList<URL>(extJarList.size());
        for (var extJar : extJarList) {
            var localPath = resourceManageService.copyFromStorageToLocal(extJar);
            copyToRemoteIfChanged(localPath, extJar);
            jarUrls.add(Paths.get(localPath).toUri().toURL());
        }
        return jarUrls;
    }

    private String getLocalPathOfSqlJarFile() {
        var sqlJarName = new Path(flinkConfig.getJarFile()).getName();
        var jobRootPath = PathUtil.getExecJobRootPath();
        var localFile = String.join(SLASH, jobRootPath, sqlJarName);
        copyToLocalIfChanged(flinkConfig.getJarFile(), localFile);
        return localFile;
    }

    // TODO: support more environments.
    private void copyToRemoteIfChanged(String localFile, String hdfsFile) {
        try {
            hadoopService.copyIfNewHdfsAndFileChanged(localFile, hdfsFile);
        } catch (Exception e) {
            throw new RuntimeException(String.format("Copy %s from local to remote hdfs failed", localFile), e);
        }
    }

    private void copyToLocalIfChanged(String hdfsFile, String localFile) {
        try {
            storageService.copyFileToLocalIfChanged(hdfsFile, localFile);
        } catch (Exception e) {
            throw new RuntimeException(String.format("Copy %s from hdfs to local disk failed", hdfsFile), e);
        }
    }

    private List<String> getExtJarPaths(FlinkJob flinkJob) {
        return ListUtils.defaultIfNull(flinkJob.getExtJars(), Collections.emptyList()).stream()
                .map(resourceId -> resourceService.getById(resourceId))
                .map(com.flink.platform.dao.entity.Resource::getFullName)
                .collect(toList());
    }
}
