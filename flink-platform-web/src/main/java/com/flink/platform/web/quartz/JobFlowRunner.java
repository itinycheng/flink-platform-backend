package com.flink.platform.web.quartz;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.service.AlertSendingService;
import com.flink.platform.web.service.JobFlowScheduleService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.flink.platform.common.constants.JobConstant.CONFIG;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.getNonTerminals;
import static com.flink.platform.common.enums.JobFlowStatus.ONLINE;
import static com.flink.platform.common.enums.JobFlowStatus.SCHEDULING;
import static com.flink.platform.common.enums.JobFlowType.JOB_LIST;

/** submit job flow. */
@Slf4j
public class JobFlowRunner implements Job {

    private static final Map<String, Object> PARALLEL_LOCK_MAP = new ConcurrentHashMap<>();

    private final JobFlowService jobFlowService = SpringContext.getBean(JobFlowService.class);

    private final JobFlowRunService jobFlowRunService = SpringContext.getBean(JobFlowRunService.class);

    private final JobRunInfoService jobRunService = SpringContext.getBean(JobRunInfoService.class);

    private final JobFlowScheduleService jobFlowScheduleService = SpringContext.getBean(JobFlowScheduleService.class);

    private final AlertSendingService alertSendingService = SpringContext.getBean(AlertSendingService.class);

    private final WorkerConfig workerConfig = SpringContext.getBean(WorkerConfig.class);

    @Override
    public void execute(JobExecutionContext context) {
        JobDetail detail = context.getJobDetail();
        JobKey key = detail.getKey();
        String code = key.getName();
        JobDataMap dataMap = detail.getJobDataMap();

        synchronized (getProcessLock(code)) {
            // Get job flow info.
            JobFlow jobFlow = jobFlowService.getOne(new QueryWrapper<JobFlow>()
                    .lambda()
                    .eq(JobFlow::getCode, code)
                    .in(JobFlow::getStatus, ONLINE, SCHEDULING));
            if (jobFlow == null) {
                log.warn("The job flow: {} isn't exists or not in scheduling status", code);
                return;
            }

            // execution config.
            ExecutionConfig executionConfig = getOrMergeExecutionConfig(dataMap, jobFlow);
            JobFlowRun jobFlowRun = jobFlowRunService.getOne(new QueryWrapper<JobFlowRun>()
                    .lambda()
                    .eq(JobFlowRun::getFlowId, jobFlow.getId())
                    .in(JobFlowRun::getStatus, getNonTerminals()));

            if (jobFlowRun != null) {
                if (JOB_LIST.equals(jobFlowRun.getType())) {
                    JobRunInfo unfinishedJob = jobRunService.getOne(new QueryWrapper<JobRunInfo>()
                            .lambda()
                            .eq(JobRunInfo::getJobId, executionConfig.getStartJobId())
                            .in(JobRunInfo::getStatus, getNonTerminals())
                            .last("limit 1"));
                    if (unfinishedJob != null) {
                        log.warn(
                                "The job: {} is in non-terminal status, job run id: {}",
                                unfinishedJob.getName(),
                                unfinishedJob.getId());
                        alertSendingService.sendErrAlerts(
                                jobFlow, "There is already a running job: " + unfinishedJob.getId());
                        return;
                    }
                } else {
                    log.warn(
                            "The job flow: {} is in non-terminal status, run id: {}",
                            jobFlow.getName(),
                            jobFlowRun.getId());
                    alertSendingService.sendErrAlerts(
                            jobFlow, "There is already a running jobFlowRun: " + jobFlowRun.getId());
                    return;
                }
            }

            // Create job flow run instance.
            jobFlowRun = new JobFlowRun();
            jobFlowRun.setFlowId(jobFlow.getId());
            jobFlowRun.setName(
                    String.join("-", jobFlow.getName(), jobFlow.getCode(), String.valueOf(System.currentTimeMillis())));
            if (JOB_LIST.equals(jobFlow.getType())) {
                jobFlowRun.setFlow(createFlowFromConfig(executionConfig));
            } else {
                jobFlowRun.setFlow(jobFlow.getFlow());
            }
            jobFlowRun.setUserId(jobFlow.getUserId());
            jobFlowRun.setHost(Constant.HOST_IP);
            jobFlowRun.setType(jobFlow.getType());
            jobFlowRun.setCronExpr(jobFlow.getCronExpr());
            jobFlowRun.setPriority(jobFlow.getPriority());
            jobFlowRun.setConfig(executionConfig);
            jobFlowRun.setTags(jobFlow.getTags());
            jobFlowRun.setAlerts(jobFlow.getAlerts());
            jobFlowRun.setTimeout(jobFlow.getTimeout());
            jobFlowRun.setStatus(SUBMITTED);
            jobFlowRunService.save(jobFlowRun);

            // register job flow run.
            jobFlowScheduleService.registerToScheduler(jobFlowRun);
            log.info(
                    "Job flow run: {} is created, job flow: {}, time: {}",
                    jobFlowRun.getId(),
                    code,
                    System.currentTimeMillis());
        }
    }

    private JobFlowDag createFlowFromConfig(ExecutionConfig executionConfig) {
        JobFlowDag flow = new JobFlowDag();
        flow.addVertex(new JobVertex(executionConfig.getStartJobId()));
        return flow;
    }

    private ExecutionConfig getOrMergeExecutionConfig(JobDataMap dataMap, JobFlow jobFlow) {
        ExecutionConfig baseConfig = JsonUtil.toBean(dataMap.getString(CONFIG), ExecutionConfig.class);
        if (baseConfig == null) {
            baseConfig = new ExecutionConfig();
        }

        ExecutionConfig templateConfig = jobFlow.getConfig();
        int parallelism = templateConfig != null && templateConfig.getParallelism() > 0
                ? templateConfig.getParallelism()
                : workerConfig.getPerFlowExecThreads();
        baseConfig.setParallelism(parallelism);
        return baseConfig;
    }

    private Object getProcessLock(String code) {
        Object newLock = new Object();
        Object lock = PARALLEL_LOCK_MAP.putIfAbsent(code, newLock);
        if (lock == null) {
            lock = newLock;
        }
        return lock;
    }
}
