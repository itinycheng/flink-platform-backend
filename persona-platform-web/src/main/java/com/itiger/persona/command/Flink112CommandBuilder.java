package com.itiger.persona.command;

import com.itiger.persona.common.exception.FlinkCommandGenException;
import com.itiger.persona.common.util.JsonUtil;
import com.itiger.persona.entity.JobInfo;
import com.itiger.persona.enums.DeployMode;
import com.itiger.persona.enums.JobType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author tiny.wang
 */
@Component("flink112CommandBuilder")
public class Flink112CommandBuilder implements JobCommandBuilder {

    private static final List<JobType> SUPPORTED_JOB_TYPES = Arrays.asList(JobType.FLINK_JAR, JobType.FLINK_SQL);

    private static final String EXEC_MODE = " %s -t %s -d ";

    @Value("${flink.sql112.command-path}")
    private String commandBinPath;

    @Value("${flink.sql112.jar-path}")
    private String sqlJarPath;

    @Value("${flink.sql112.class-name}")
    private String sqlClassName;

    @Resource(name = "sqlContextHelper")
    private SqlContextHelper sqlContextHelper;

    @Override
    public boolean isSupported(JobType jobType) {
        return SUPPORTED_JOB_TYPES.contains(jobType);
    }

    @Override
    public JobCommand buildCommand(JobInfo jobInfo) {
        JobCommand command = new JobCommand();
        DeployMode deployMode = jobInfo.getDeployMode();
        String execMode = String.format(EXEC_MODE, deployMode.mode, deployMode.target);
        command.setPrefix(commandBinPath + execMode);
        Map<String, Object> configs = command.getConfigs();
        configs.putAll(JsonUtil.toJsonMap(jobInfo.getConfig()));
        String appName = String.join("-", jobInfo.getName(), jobInfo.getCode());
        configs.put(Constants.YARN_NAME, appName);
        command.setExtJars(JsonUtil.toJsonList(jobInfo.getExtJars()));
        switch (jobInfo.getType()) {
            case FLINK_JAR:
                command.setMainJar(jobInfo.getSubject());
                command.setMainArgs(jobInfo.getMainArgs());
                command.setMainClass(jobInfo.getMainClass());
                break;
            case FLINK_SQL:
                String filePath = sqlContextHelper.convertFromAndSaveToFile(jobInfo);
                command.setMainArgs(filePath);
                command.setMainJar(sqlJarPath);
                command.setMainClass(sqlClassName);
                break;
            default:
                throw new FlinkCommandGenException("unsupported job type");
        }
        return command;
    }
}
