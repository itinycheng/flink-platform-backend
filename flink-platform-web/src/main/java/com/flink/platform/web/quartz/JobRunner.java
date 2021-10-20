package com.flink.platform.web.quartz;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.JobStatusEnum;
import com.flink.platform.common.enums.JobYarnStatusEnum;
import com.flink.platform.common.exception.JobCommandGenException;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.FlinkCommand;
import com.flink.platform.web.command.JobCallback;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.comn.SpringContext;
import com.flink.platform.web.entity.JobInfo;
import com.flink.platform.web.entity.JobRunInfo;
import com.flink.platform.web.enums.JobType;
import com.flink.platform.web.enums.SqlVar;
import com.flink.platform.web.service.IJobInfoService;
import com.flink.platform.web.service.IJobRunInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.toMap;

/** submit job. */
@Slf4j
public class JobRunner implements Job {

    private static final Map<String, Long> RUNNER_MAP = new ConcurrentHashMap<>();

    private final IJobInfoService jobInfoService = SpringContext.getBean(IJobInfoService.class);

    private final IJobRunInfoService jobRunInfoService =
            SpringContext.getBean(IJobRunInfoService.class);

    private final List<CommandExecutor> jobCommandExecutors =
            SpringContext.getBeansOfType(CommandExecutor.class);

    private final List<CommandBuilder> jobCommandBuilders =
            SpringContext.getBeansOfType(CommandBuilder.class);

    @Override
    public void execute(JobExecutionContext context) {
        JobDetail detail = context.getJobDetail();
        JobKey key = detail.getKey();
        String code = key.getName();
        JobCommand jobCommand = null;
        JobInfo jobInfo = null;
        try {
            // TODO avoid preforming the same job multiple times at the same time
            Long previous = RUNNER_MAP.putIfAbsent(code, System.currentTimeMillis());
            if (previous != null && previous > 0) {
                log.warn("the job: {} is already running, start time: {}", code, previous);
                return;
            }

            // step 1: get job info
            jobInfo =
                    jobInfoService.getOne(
                            new QueryWrapper<JobInfo>()
                                    .lambda()
                                    .eq(JobInfo::getCode, code)
                                    .in(
                                            JobInfo::getStatus,
                                            JobStatusEnum.SCHEDULED.getCode(),
                                            JobStatusEnum.READY.getCode()));
            if (jobInfo == null) {
                log.warn("the job is no longer exists or not in ready/scheduled status, {}", code);
                return;
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

            // step 3: build shell command, create a SqlContext if needed
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
        } catch (Exception e) {
            log.error("cannot exec job: {}", code, e);
        } finally {
            RUNNER_MAP.remove(code);
            if (jobInfo != null && jobInfo.getType() == JobType.FLINK_SQL && jobCommand != null) {
                try {
                    FlinkCommand flinkCommand = (FlinkCommand) jobCommand;
                    if (flinkCommand.getMainArgs() != null) {
                        Files.deleteIfExists(Paths.get(flinkCommand.getMainArgs()));
                    }
                } catch (Exception e) {
                    log.warn("delete sql context file failed", e);
                }
            }
        }

        // print job command info
        String command = jobCommand != null ? jobCommand.toCommandString() : null;
        log.info(" job key: {}, time: {}, command: {}", key, System.currentTimeMillis(), command);
    }
}
