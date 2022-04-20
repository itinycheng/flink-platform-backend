package com.flink.platform.web.command;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.exception.JobCommandGenException;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.task.FlinkJob;
import com.flink.platform.dao.service.ResourceService;
import com.flink.platform.web.config.FlinkConfig;
import com.flink.platform.web.service.HdfsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Resource;

import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.flink.platform.common.constants.Constant.ROOT_DIR;
import static com.flink.platform.common.constants.Constant.SEMICOLON;
import static com.flink.platform.common.constants.Constant.SLASH;
import static com.flink.platform.common.constants.JobConstant.YARN_APPLICATION_NAME;
import static com.flink.platform.common.constants.JobConstant.YARN_PROVIDED_LIB_DIRS;

/** Flink command builder. */
@Slf4j
public abstract class FlinkCommandBuilder implements CommandBuilder {

    private static final List<JobType> SUPPORTED_JOB_TYPES =
            Arrays.asList(JobType.FLINK_JAR, JobType.FLINK_SQL);

    private static final String EXEC_MODE = " %s -t %s -d ";

    @Value("${flink.local.jar-dir}")
    private String jobJarDir;

    @Resource(name = "sqlContextHelper")
    private SqlContextHelper sqlContextHelper;

    @Resource private HdfsService hdfsService;

    @Resource private ResourceService resourceService;

    private final FlinkConfig flinkConfig;

    public FlinkCommandBuilder(FlinkConfig flinkConfig) {
        this.flinkConfig = flinkConfig;
    }

    @Override
    public boolean isSupported(JobType jobType, String version) {
        return SUPPORTED_JOB_TYPES.contains(jobType) && flinkConfig.getVersion().equals(version);
    }

    @Override
    public JobCommand buildCommand(JobInfo jobInfo) throws Exception {
        FlinkJob flinkJob = jobInfo.getConfig().unwrap(FlinkJob.class);
        initResources(flinkJob);
        FlinkCommand command = new FlinkCommand();
        DeployMode deployMode = jobInfo.getDeployMode();
        String execMode = String.format(EXEC_MODE, deployMode.mode, deployMode.target);
        command.setPrefix(flinkConfig.getCommandPath() + execMode);
        // add configurations
        Map<String, Object> configs = command.getConfigs();
        if (flinkJob.getConfigs() != null) {
            configs.putAll(flinkJob.getConfigs());
        }
        // add yarn application name
        configs.put(YARN_APPLICATION_NAME, createAppName(jobInfo));
        // add lib dirs and user classpaths
        configs.put(YARN_PROVIDED_LIB_DIRS, getMergedLibDirs(flinkJob.getExtJarPaths()));
        List<URL> classpaths = getOrCreateClasspaths(jobInfo.getCode(), flinkJob.getExtJarPaths());
        command.setClasspaths(classpaths);
        switch (jobInfo.getType()) {
            case FLINK_JAR:
                String localPathOfMainJar =
                        getLocalPathOfMainJar(jobInfo.getCode(), jobInfo.getSubject());
                command.setMainJar(localPathOfMainJar);
                command.setMainArgs(flinkJob.getMainArgs());
                command.setMainClass(flinkJob.getMainClass());
                command.setOptionArgs(flinkJob.getOptionArgs());
                break;
            case FLINK_SQL:
                String localJarPath = getLocalPathOfSqlJarFile();
                String filePath = sqlContextHelper.convertFromAndSaveToFile(jobInfo);
                command.setMainArgs(filePath);
                command.setMainJar(localJarPath);
                command.setMainClass(flinkConfig.getClassName());
                break;
            default:
                throw new JobCommandGenException("unsupported job type");
        }
        return command;
    }

    private String createAppName(JobInfo jobInfo) {
        String jobName = jobInfo.getName().replaceAll("\\s+", "");
        return String.join("-", jobInfo.getExecMode().name(), jobInfo.getCode(), jobName);
    }

    private String getLocalPathOfMainJar(String jobCode, String jarPath) {
        if (!jarPath.toLowerCase().startsWith("hdfs")) {
            return jarPath;
        }

        String jarName = new Path(jarPath).getName();
        String localJarPath = String.join(SLASH, ROOT_DIR, jobJarDir, jobCode, jarName);
        copyToLocalIfChanged(jarPath, localJarPath);
        return localJarPath;
    }

    private Object getMergedLibDirs(List<String> extJarList) {
        String userLibDirs =
                extJarList.stream()
                        .map(s -> s.substring(0, s.lastIndexOf(SLASH)))
                        .distinct()
                        .collect(Collectors.joining(SEMICOLON));
        return userLibDirs.length() > 0
                ? String.join(SEMICOLON, flinkConfig.getLibDirs(), userLibDirs)
                : flinkConfig.getLibDirs();
    }

    private List<URL> getOrCreateClasspaths(String jobCode, List<String> extJarList)
            throws Exception {
        List<URL> classpaths = new ArrayList<>(extJarList.size());
        for (String hdfsExtJar : extJarList) {
            String extJarName = new Path(hdfsExtJar).getName();
            String localPath = String.join(SLASH, ROOT_DIR, jobJarDir, jobCode, extJarName);
            copyToLocalIfChanged(hdfsExtJar, localPath);
            classpaths.add(Paths.get(localPath).toUri().toURL());
        }
        return classpaths;
    }

    private String getLocalPathOfSqlJarFile() {
        String sqlJarName = new Path(flinkConfig.getJarFile()).getName();
        String localFile = String.join(SLASH, ROOT_DIR, jobJarDir, sqlJarName);
        copyToLocalIfChanged(flinkConfig.getJarFile(), localFile);
        return localFile;
    }

    private void copyToLocalIfChanged(String hdfsFile, String localFile) {
        try {
            hdfsService.copyFileToLocalIfChanged(new Path(hdfsFile), new Path(localFile));
        } catch (Exception e) {
            throw new JobCommandGenException(
                    String.format("Copy %s from hdfs to local disk failed", hdfsFile), e);
        }
    }

    private void initResources(FlinkJob flinkJob) {
        List<String> jarPaths =
                ListUtils.defaultIfNull(flinkJob.getExtJars(), Collections.emptyList()).stream()
                        .map(resourceId -> resourceService.getById(resourceId))
                        .map(com.flink.platform.dao.entity.Resource::getFullName)
                        .collect(Collectors.toList());
        flinkJob.setExtJarPaths(jarPaths);
    }
}
