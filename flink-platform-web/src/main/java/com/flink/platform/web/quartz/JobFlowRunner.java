package com.flink.platform.web.quartz;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.common.util.NumberUtil;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobFlowDag.NodeLayout;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.service.JobFlowScheduleService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.flink.platform.common.constants.JobConstant.CONFIG;
import static com.flink.platform.common.constants.JobConstant.FLOW_RUN_ID;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.JobFlowStatus.ONLINE;
import static com.flink.platform.common.enums.JobFlowStatus.SCHEDULING;
import static com.flink.platform.common.enums.JobFlowType.JOB_LIST;

/** submit job flow. */
@Slf4j
public class JobFlowRunner implements Job {

    private static final Map<String, Object> PARALLEL_LOCK_MAP = new ConcurrentHashMap<>();

    private final JobFlowService jobFlowService = SpringContext.getBean(JobFlowService.class);

    private final JobFlowRunService jobFlowRunService = SpringContext.getBean(JobFlowRunService.class);

    private final JobInfoService jobInfoService = SpringContext.getBean(JobInfoService.class);

    private final JobRunInfoService jobRunService = SpringContext.getBean(JobRunInfoService.class);

    private final JobFlowScheduleService jobFlowScheduleService = SpringContext.getBean(JobFlowScheduleService.class);

    private final AlertSendingService alertSendingService = SpringContext.getBean(AlertSendingService.class);

    private final WorkerConfig workerConfig = SpringContext.getBean(WorkerConfig.class);

    // TODO: handle exceptions ?
    @Override
    public void execute(JobExecutionContext context) {
        var detail = context.getJobDetail();
        var key = detail.getKey();
        var code = key.getName();
        var dataMap = detail.getJobDataMap();

        synchronized (getProcessLock(code)) {
            // Get job flow info.
            var jobFlow = jobFlowService.getOne(new QueryWrapper<JobFlow>()
                    .lambda()
                    .eq(JobFlow::getCode, code)
                    .in(JobFlow::getStatus, ONLINE, SCHEDULING));
            if (jobFlow == null) {
                log.warn("The job flow: {} isn't exists or not in scheduling status", code);
                return;
            }

            var flowRunId = NumberUtil.toLong(dataMap.get(FLOW_RUN_ID));
            var executionConfig = getOrMergeExecutionConfig(dataMap, jobFlow);

            if (JOB_LIST.equals(jobFlow.getType())) {
                var runningJob = jobRunService.findRunningJob(executionConfig.getStartJobId());
                if (runningJob != null) {
                    log.warn(
                            "The job: {} is in non-terminal status, job run id: {}",
                            runningJob.getName(),
                            runningJob.getId());
                    alertSendingService.sendErrAlerts(jobFlow, "There is already a running job: " + runningJob.getId());
                    return;
                }
            } else {
                var runningFlow = jobFlowRunService.findRunningFlow(jobFlow.getId(), executionConfig);
                if (runningFlow != null && !runningFlow.getId().equals(flowRunId)) {
                    log.warn(
                            "The job flow: {} is in non-terminal status, run id: {}",
                            jobFlow.getName(),
                            runningFlow.getId());
                    alertSendingService.sendErrAlerts(
                            jobFlow, "There is already a running jobFlowRun: " + runningFlow.getId());
                    return;
                }
            }

            // Create job flow run instance.
            var jobFlowRun = new JobFlowRun();
            jobFlowRun.setId(flowRunId);
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
            jobFlowRun.setParams(jobFlow.getParams());
            jobFlowRun.setStatus(SUBMITTED);
            jobFlowRunService.saveOrUpdate(jobFlowRun);

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
        Long startJobId = executionConfig.getStartJobId();
        flow.addVertex(new JobVertex(startJobId));
        // node layouts.
        JobInfo jobInfo = jobInfoService.getById(startJobId);
        String classification = jobInfo.getType().getClassification();
        Map<Long, NodeLayout> nodeLayouts = flow.getNodeLayouts();
        nodeLayouts.put(startJobId, new NodeLayout(null, classification, 0, 0));
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
