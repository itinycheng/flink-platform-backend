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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.itiger.persona.common.constants.JobConstant.ROOT_DIR;
import static com.itiger.persona.common.constants.JobConstant.YARN_NAME;

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
        Map<String, Object> configs = command.getConfigs();
        configs.putAll(JsonUtil.toMap(jobInfo.getConfig()));
        String appName = String.join("-", jobInfo.getExecMode().name(), jobInfo.getCode());
        configs.put(YARN_NAME, appName);
        command.setExtJars(JsonUtil.toList(jobInfo.getExtJars()));
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

    private String getLocalPathOfSqlJarFile() {
        Path hdfsJarPath = new Path(hdfsJarFile);
        String sqlJarName = hdfsJarPath.getName();
        String localFile = String.join("/", ROOT_DIR, jobJarDir, sqlJarName);
        try {
            hdfsService.copyFileToLocalIfChanged(hdfsJarPath, new Path(localFile));
        } catch (Exception e) {
            log.error("copy {} from hdfs to local disk failed", sqlJarName, e);
        }
        return localFile;
    }
}
