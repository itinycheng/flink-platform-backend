package com.flink.platform.web.quartz;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.common.util.DateUtil;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.common.util.NumberUtil;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobFlowDag.NodeLayout;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.model.FlowRunCreationResult;
import com.flink.platform.web.service.DependencyService;
import com.flink.platform.web.service.FlowScheduleService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.flink.platform.common.constants.JobConstant.CONFIG;
import static com.flink.platform.common.constants.JobConstant.FLOW_RUN_ID;
import static com.flink.platform.common.constants.JobConstant.SCHEDULE_TIME;
import static com.flink.platform.common.constants.JobConstant.USER_ID;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static com.flink.platform.common.enums.JobFlowStatus.ONLINE;
import static com.flink.platform.common.enums.JobFlowStatus.SCHEDULING;
import static com.flink.platform.common.enums.JobFlowType.JOB_LIST;
import static com.flink.platform.common.util.Preconditions.checkNotNull;
import static com.flink.platform.web.service.DependencyService.DependencyState.SATISFIED;
import static java.time.temporal.ChronoUnit.SECONDS;

/** submit job flow. */
@Slf4j
public class JobFlowRunner implements Job {

    private static final Map<String, Object> PARALLEL_LOCK_MAP = new ConcurrentHashMap<>();

    private final JobFlowService jobFlowService = SpringContext.getBean(JobFlowService.class);

    private final JobFlowRunService jobFlowRunService = SpringContext.getBean(JobFlowRunService.class);

    private final JobInfoService jobInfoService = SpringContext.getBean(JobInfoService.class);

    private final JobRunInfoService jobRunService = SpringContext.getBean(JobRunInfoService.class);

    private final AlertSendingService alertSendingService = SpringContext.getBean(AlertSendingService.class);

    private final WorkerConfig workerConfig = SpringContext.getBean(WorkerConfig.class);

    private final DependencyService dependencyService = SpringContext.getBean(DependencyService.class);

    private final FlowScheduleService flowScheduleService = SpringContext.getBean(FlowScheduleService.class);

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
            var scheduleTime = ObjectUtils.firstNonNull(
                            DateUtil.parseNullable(dataMap.getString(SCHEDULE_TIME)),
                            DateUtil.toLocalDateTime(context.getScheduledFireTime()),
                            LocalDateTime.now())
                    .truncatedTo(SECONDS);

            // Create job flow run instance.
            var jobFlowRun = new JobFlowRun();
            jobFlowRun.setId(flowRunId);
            jobFlowRun.setFlowId(jobFlow.getId());
            jobFlowRun.setName(
                    String.join("-", jobFlow.getName(), jobFlow.getCode(), String.valueOf(System.currentTimeMillis())));
            jobFlowRun.setFlow(jobFlow.getFlow());
            var triggerUserId = NumberUtil.toLong(dataMap.get(USER_ID));
            jobFlowRun.setUserId(triggerUserId != null ? triggerUserId : jobFlow.getUserId());
            jobFlowRun.setWorkspaceId(jobFlow.getWorkspaceId());
            jobFlowRun.setHost(Constant.HOST_IP);
            jobFlowRun.setType(jobFlow.getType());
            jobFlowRun.setCronExpr(jobFlow.getCronExpr());
            jobFlowRun.setPriority(jobFlow.getPriority());
            jobFlowRun.setConfig(executionConfig);
            jobFlowRun.setTags(jobFlow.getTags());
            jobFlowRun.setAlerts(jobFlow.getAlerts());
            jobFlowRun.setTimeout(jobFlow.getTimeout());
            jobFlowRun.setParams(jobFlow.getParams());
            jobFlowRun.setScheduleTime(scheduleTime);
            // dependsOn
            var dependsOn = jobFlow.getDependsOn();
            jobFlowRun.setDependsOn(dependsOn);
            jobFlowRun.setStatus(dependencyService.resolveAndEvaluate(dependsOn) == SATISFIED ? SUBMITTED : WAITING);

            // TODO: legacy, Remove in the future.
            if (JOB_LIST.equals(jobFlowRun.getType())) {
                handleJobListRun(jobFlowRun, executionConfig);
                return;
            }

            // FLOW / FLINK / SPARK / etc.
            var result = flowScheduleService.tryCreateFlowRun(jobFlowRun);
            handleFlowRunCreationResult(result, jobFlow, jobFlowRun);
        }
    }

    private void handleFlowRunCreationResult(FlowRunCreationResult result, JobFlow jobFlow, JobFlowRun jobFlowRun) {
        result.timeouts().forEach(timeout -> {
            log.warn(
                    "{}, failed waiting flow run: {}",
                    timeout.reason(),
                    timeout.run().getId());
            alertSendingService.sendAlerts(timeout.run(), timeout.reason());
        });

        switch (result.state()) {
            case BLOCKED -> {
                var blockOn = checkNotNull(result.blockOn());
                var reason = result.blockReason();
                log.warn("Job flow {} rejected: {}, conflicting run id: {}", jobFlow.getId(), reason, blockOn.getId());
                alertSendingService.sendErrAlerts(
                        jobFlow, "Flow run rejected: " + reason + ", block on: " + blockOn.getId());
            }
            case CREATED ->
                log.info("Job flow run: {} is created, time: {}", jobFlowRun.getId(), System.currentTimeMillis());
        }
    }

    private JobFlowDag createJobListFlow(ExecutionConfig executionConfig) {
        var flow = new JobFlowDag();
        var startJobId = executionConfig.getStartJobId();
        flow.addVertex(new JobVertex(startJobId));
        // node layouts.
        var jobInfo = jobInfoService.getById(startJobId);
        var classification = jobInfo.getType().getClassification();
        var nodeLayouts = flow.getNodeLayouts();
        nodeLayouts.put(startJobId, new NodeLayout(null, classification, 0, 0));
        return flow;
    }

    private ExecutionConfig getOrMergeExecutionConfig(JobDataMap dataMap, JobFlow jobFlow) {
        var executionConfig = new ExecutionConfig();
        executionConfig.setMaxJobsPerRun(workerConfig.getPerFlowExecThreads());
        executionConfig.setMaxActiveRuns(1);
        executionConfig.mergeFrom(jobFlow.getConfig());
        executionConfig.mergeFrom(JsonUtil.toBean(dataMap.getString(CONFIG), ExecutionConfig.class));
        executionConfig.setMaxJobsPerRun(
                Math.min(executionConfig.getMaxJobsPerRun(), workerConfig.getMaxPerFlowExecThreads()));
        return executionConfig;
    }

    private void handleJobListRun(JobFlowRun jobFlowRun, ExecutionConfig executionConfig) {
        if (!JOB_LIST.equals(jobFlowRun.getType())) {
            return;
        }

        var activeRun = jobRunService.findActiveRunByJobId(executionConfig.getStartJobId());
        if (activeRun != null) {
            log.warn("The job: {} is in non-terminal status, job run id: {}", activeRun.getName(), activeRun.getId());
            jobFlowRun.setStatus(FAILURE);
            alertSendingService.sendAlertsDirectly(jobFlowRun, "There is already an active run: " + activeRun.getId());
            return;
        }

        // update flow for jobs of JOB_LIST type.
        jobFlowRun.setFlow(createJobListFlow(executionConfig));
        jobFlowRunService.saveOrUpdate(jobFlowRun);
        log.info(
                "The job flow of type JOB_LIST: {} is created, time: {}",
                jobFlowRun.getId(),
                System.currentTimeMillis());
    }

    private Object getProcessLock(String code) {
        var newLock = new Object();
        var lock = PARALLEL_LOCK_MAP.putIfAbsent(code, newLock);
        if (lock == null) {
            lock = newLock;
        }
        return lock;
    }
}
