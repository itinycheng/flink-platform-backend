package com.itiger.persona.command;

import com.itiger.persona.common.exception.FlinkCommandGenException;
import com.itiger.persona.common.util.JsonUtil;
import com.itiger.persona.entity.JobInfo;
import com.itiger.persona.enums.DeployMode;
import com.itiger.persona.enums.JobType;
import com.itiger.persona.service.HdfsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.itiger.persona.common.constants.Constant.SEMICOLON;
import static com.itiger.persona.common.constants.Constant.SLASH;
import static com.itiger.persona.common.constants.JobConstant.ROOT_DIR;
import static com.itiger.persona.common.constants.JobConstant.YARN_APPLICATION_NAME;
import static com.itiger.persona.common.constants.JobConstant.YARN_PROVIDED_LIB_DIRS;

/**
 * @author tiny.wang
 */
@Slf4j
@Component("flink112CommandBuilder")
public class Flink112CommandBuilder implements JobCommandBuilder {

    private static final List<JobType> SUPPORTED_JOB_TYPES = Arrays.asList(JobType.FLINK_JAR, JobType.FLINK_SQL);

    private static final String EXEC_MODE = " %s -t %s -d ";

    @Value("${flink.sql112.command}")
    private String commandBinPath;

    @Value("${flink.sql112.jar-file}")
    private String hdfsJarFile;

    @Value("${flink.sql112.class-name}")
    private String sqlClassName;

    @Value("${flink.sql112.yarn-provided-lib-dirs}")
    private String providedLibDirs;

    @Value("${flink.local.jar-dir}")
    private String jobJarDir;

    @Resource(name = "sqlContextHelper")
    private SqlContextHelper sqlContextHelper;

    @Resource
    private HdfsService hdfsService;

    @Override
    public boolean isSupported(JobType jobType) {
        return SUPPORTED_JOB_TYPES.contains(jobType);
    }

    @Override
    public JobCommand buildCommand(JobInfo jobInfo) throws Exception {
        JobCommand command = new JobCommand();
        DeployMode deployMode = jobInfo.getDeployMode();
        String execMode = String.format(EXEC_MODE, deployMode.mode, deployMode.target);
        command.setPrefix(commandBinPath + execMode);
        // add configurations
        Map<String, Object> configs = command.getConfigs();
        configs.putAll(JsonUtil.toMap(jobInfo.getConfig()));
        // add yarn application name
        String appName = String.join("-", jobInfo.getExecMode().name(), jobInfo.getCode());
        configs.put(YARN_APPLICATION_NAME, appName);
        // add lib dirs and user classpaths
        List<String> extJarList = JsonUtil.toList(jobInfo.getExtJars());
        configs.put(YARN_PROVIDED_LIB_DIRS, getMergedLibDirs(extJarList));
        List<URL> classpaths = getOrCreateClasspaths(jobInfo.getCode(), extJarList);
        command.setClasspaths(classpaths);
        switch (jobInfo.getType()) {
            case FLINK_JAR:
                command.setMainJar(jobInfo.getSubject());
                command.setMainArgs(jobInfo.getMainArgs());
                command.setMainClass(jobInfo.getMainClass());
                break;
            case FLINK_SQL:
                String localJarPath = getLocalPathOfSqlJarFile();
                String filePath = sqlContextHelper.convertFromAndSaveToFile(jobInfo);
                command.setMainArgs(filePath);
                command.setMainJar(localJarPath);
                command.setMainClass(sqlClassName);
                break;
            default:
                throw new FlinkCommandGenException("unsupported job type");
        }
        return command;
    }

    private Object getMergedLibDirs(List<String> extJarList) {
        String userLibDirs = extJarList.stream().map(s -> s.substring(0, s.lastIndexOf(SLASH)))
                .distinct().collect(Collectors.joining(SEMICOLON));
        return userLibDirs.length() > 0
                ? String.join(SEMICOLON, providedLibDirs, userLibDirs)
                : providedLibDirs;
    }

    private List<URL> getOrCreateClasspaths(String jobCode, List<String> extJarList) throws Exception {
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
        String sqlJarName = new Path(hdfsJarFile).getName();
        String localFile = String.join(SLASH, ROOT_DIR, jobJarDir, sqlJarName);
        copyToLocalIfChanged(hdfsJarFile, localFile);
        return localFile;
    }

    private void copyToLocalIfChanged(String hdfsFile, String localFile) {
        try {
            hdfsService.copyFileToLocalIfChanged(new Path(hdfsFile), new Path(localFile));
        } catch (Exception e) {
            throw new FlinkCommandGenException(String.format("Copy %s from hdfs to local disk failed", hdfsFile), e);
        }
    }
}
