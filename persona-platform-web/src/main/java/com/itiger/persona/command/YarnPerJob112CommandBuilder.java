package com.itiger.persona.command;

import com.itiger.persona.entity.JobInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author tiny.wang
 */
@Component("yarnPerJobCommandBuilder")
public class YarnPerJob112CommandBuilder implements JobCommandBuilder {

    @Value("${flink.sql112.command-path}")
    private String commandBinPath;

    @Value("${flink.sql112.jar-path}")
    private String sqlJarPath;

    @Override
    public String buildCommand(JobInfo jobInfo) {
        return null;
    }
}
