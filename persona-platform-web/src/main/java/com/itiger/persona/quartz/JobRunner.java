package com.itiger.persona.quartz;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itiger.persona.command.CommandExecutor;
import com.itiger.persona.command.JobCallback;
import com.itiger.persona.command.JobCommand;
import com.itiger.persona.command.JobCommandBuilder;
import com.itiger.persona.common.enums.JobStatusEnum;
import com.itiger.persona.common.exception.FlinkCommandGenException;
import com.itiger.persona.common.util.JsonUtil;
import com.itiger.persona.comn.SpringContext;
import com.itiger.persona.entity.JobInfo;
import com.itiger.persona.entity.JobRunInfo;
import com.itiger.persona.enums.JobType;
import com.itiger.persona.enums.SqlVar;
import com.itiger.persona.service.IJobInfoService;
import com.itiger.persona.service.IJobRunInfoService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * submit job
 *
 * @author tiny.wang
 */
@Slf4j
public class JobRunner implements Job {

    private static final Map<String, Long> RUNNER_MAP = new ConcurrentHashMap<>();

    private final IJobInfoService jobInfoService = SpringContext.getBean(IJobInfoService.class);

    private final IJobRunInfoService jobRunInfoService = SpringContext.getBean(IJobRunInfoService.class);

    private final CommandExecutor commandExecutor = SpringContext.getBean(CommandExecutor.class);

    private final List<JobCommandBuilder> jobCommandBuilders = SpringContext.getBeansOfType(JobCommandBuilder.class);

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
            jobInfo = jobInfoService.getOne(new QueryWrapper<JobInfo>().lambda()
                    .eq(JobInfo::getCode, code).in(JobInfo::getStatus,
                            JobStatusEnum.SCHEDULED.getCode(),
                            JobStatusEnum.READY.getCode()));
            if (jobInfo == null) {
                log.warn("the job: {} is no longer exists or not in ready/scheduled status, {}", code, jobInfo);
                return;
            }

            // step 2: replace variables in the sql statement
            for (SqlVar sqlVar : SqlVar.values()) {
                String originSubject = jobInfo.getSubject();
                String distSubject = originSubject.replace(sqlVar.variable, sqlVar.valueProvider.apply(jobInfo).toString());
                jobInfo.setSubject(distSubject);
            }

            // step 3: build shell command, create a SqlContext if needed
            JobType jobType = jobInfo.getType();
            jobCommand = jobCommandBuilders.stream()
                    .filter(builder -> builder.isSupported(jobType))
                    .findFirst()
                    .orElseThrow(() -> new FlinkCommandGenException("no available job command builder"))
                    .buildCommand(jobInfo);

            // step 4: submit job
            JobCallback callback = commandExecutor.execCommand(jobCommand.toCommandString());

            // step 5: write msg back to db
            JobRunInfo jobRunInfo = new JobRunInfo();
            jobRunInfo.setJobId(jobInfo.getId());
            jobRunInfo.setStatus(0);
            jobRunInfo.setBackInfo(JsonUtil.toJsonString(callback));
            jobRunInfo.setSubmitUser("quartz");
            jobRunInfo.setSubmitTime(LocalDateTime.now());
            jobRunInfoService.saveOrUpdate(jobRunInfo);
        } catch (Exception e) {
            log.error("cannot exec job: {}", code, e);
        } finally {
            RUNNER_MAP.remove(code);
            if (jobInfo != null
                    && jobInfo.getType() == JobType.FLINK_SQL
                    && jobCommand != null
                    && jobCommand.getMainArgs() != null) {
                try {
                    Files.deleteIfExists(Paths.get(jobCommand.getMainArgs()));
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