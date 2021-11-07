package com.flink.platform.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.enums.JobYarnStatusEnum;
import com.flink.platform.common.exception.JobCommandGenException;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.FlinkCommand;
import com.flink.platform.web.command.JobCallback;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.enums.SqlVar;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

/** Process job service. */
@Slf4j
@Service
public class ProcessJobService {

    private final JobInfoService jobInfoService;

    private final JobRunInfoService jobRunInfoService;

    private final List<CommandBuilder> jobCommandBuilders;

    private final List<CommandExecutor> jobCommandExecutors;

    @Autowired
    public ProcessJobService(
            JobInfoService jobInfoService,
            JobRunInfoService jobRunInfoService,
            List<CommandBuilder> jobCommandBuilders,
            List<CommandExecutor> jobCommandExecutors) {
        this.jobInfoService = jobInfoService;
        this.jobRunInfoService = jobRunInfoService;
        this.jobCommandBuilders = jobCommandBuilders;
        this.jobCommandExecutors = jobCommandExecutors;
    }

    public Long processJob(final String jobCode) throws Exception {
        JobCommand jobCommand = null;
        JobInfo jobInfo = null;

        try {
            // step 1: get job info
            jobInfo =
                    jobInfoService.getOne(
                            new QueryWrapper<JobInfo>()
                                    .lambda()
                                    .eq(JobInfo::getCode, jobCode)
                                    .ne(JobInfo::getStatus, JobStatus.DELETE.getCode()));
            if (jobInfo == null) {
                throw new JobCommandGenException(
                        String.format(
                                "The job: %s is no longer exists or in delete status.", jobCode));
            }

            // step 2: replace variables in the sql statement
            // parse all variables in subject
            JobInfo finalJobInfo = jobInfo;
            Map<SqlVar, String> sqlVarValueMap =
                    Arrays.stream(SqlVar.values())
                            .filter(sqlVar -> finalJobInfo.getSubject().contains(sqlVar.variable))
                            .map(
                                    sqlVar ->
                                            Pair.of(
                                                    sqlVar,
                                                    sqlVar.valueProvider
                                                            .apply(finalJobInfo)
                                                            .toString()))
                            .collect(toMap(Pair::getLeft, Pair::getRight));
            // replace variable with actual value
            for (Map.Entry<SqlVar, String> entry : sqlVarValueMap.entrySet()) {
                String originSubject = jobInfo.getSubject();
                String distSubject =
                        originSubject.replace(entry.getKey().variable, entry.getValue());
                jobInfo.setSubject(distSubject);
            }

            JobType jobType = jobInfo.getType();
            String version = jobInfo.getVersion();

            // step 3: build job command, create a SqlContext if needed
            jobCommand =
                    jobCommandBuilders.stream()
                            .filter(builder -> builder.isSupported(jobType, version))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new JobCommandGenException(
                                                    "No available job command builder"))
                            .buildCommand(jobInfo);

            // step 4: submit job
            JobCallback callback =
                    jobCommandExecutors.stream()
                            .filter(executor -> executor.isSupported(jobType))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new JobCommandGenException(
                                                    "No available job command executor"))
                            .execCommand(jobCommand.toCommandString());

            // step 5: write msg back to db
            JobRunInfo jobRunInfo = new JobRunInfo();
            jobRunInfo.setJobId(jobInfo.getId());
            jobRunInfo.setStatus(JobYarnStatusEnum.NEW.getCode());
            jobRunInfo.setVariables(JsonUtil.toJsonString(sqlVarValueMap));
            jobRunInfo.setBackInfo(JsonUtil.toJsonString(callback));
            jobRunInfo.setSubmitUser("quartz");
            jobRunInfo.setSubmitTime(LocalDateTime.now());
            jobRunInfoService.saveOrUpdate(jobRunInfo);

            // step 6: print job command info
            log.info(
                    "Job code: {}, time: {}, command: {}",
                    jobCode,
                    System.currentTimeMillis(),
                    jobCommand.toCommandString());

            return jobRunInfo.getId();
        } finally {
            if (jobInfo != null && jobInfo.getType() == JobType.FLINK_SQL && jobCommand != null) {
                try {
                    FlinkCommand flinkCommand = (FlinkCommand) jobCommand;
                    if (flinkCommand.getMainArgs() != null) {
                        Files.deleteIfExists(Paths.get(flinkCommand.getMainArgs()));
                    }
                } catch (Exception e) {
                    log.warn("Delete sql context file failed", e);
                }
            }
        }
    }
}
