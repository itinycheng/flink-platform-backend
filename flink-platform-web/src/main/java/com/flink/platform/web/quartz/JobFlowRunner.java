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
import com.flink.platform.web.service.DependencyService;
import com.flink.platform.web.service.JobFlowRunBizService;
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
import static com.flink.platform.common.enums.ExecutionStatus.CREATED;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static com.flink.platform.common.enums.JobFlowStatus.ONLINE;
import static com.flink.platform.common.enums.JobFlowStatus.SCHEDULING;
import static com.flink.platform.common.enums.JobFlowType.JOB_LIST;
import static com.flink.platform.web.service.DependencyService.DependencyState.SATISFIED;
import static java.time.temporal.ChronoUnit.SECONDS;

/** submit job flow. */
@Slf4j
public class JobFlowRunner implements Job {

    private static final Map<String, Object> PARALLEL_LOCK_MAP = new ConcurrentHashMap<>();

    private final JobFlowService jobFlowService = SpringContext.getBean(JobFlowService.class);

    private final JobFlowRunService jobFlowRunService = SpringContext.getBean(JobFlowRunService.class);

    private final JobFlowRunBizService flowRunBizService = SpringContext.getBean(JobFlowRunBizService.class);

    private final JobInfoService jobInfoService = SpringContext.getBean(JobInfoService.class);

    private final JobRunInfoService jobRunService = SpringContext.getBean(JobRunInfoService.class);

    private final AlertSendingService alertSendingService = SpringContext.getBean(AlertSendingService.class);

    private final WorkerConfig workerConfig = SpringContext.getBean(WorkerConfig.class);

    private final DependencyService dependencyService = SpringContext.getBean(DependencyService.class);

    // TODO: handle exceptions ?
    @Override
    public void execute(JobExecutionContext context) {
        var detail = context.getJobDetail();
        var key = detail.getKey();
        var code = key.getName();
        var dataMap = detail.getJobDataMap();

        // TODO: Make flow admission atomic across cluster nodes.
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

            if (JOB_LIST.equals(jobFlow.getType())) {
                var activeRun = jobRunService.findActiveRunByJobId(executionConfig.getStartJobId());
                if (activeRun != null) {
                    log.warn(
                            "The job: {} is in non-terminal status, job run id: {}",
                            activeRun.getName(),
                            activeRun.getId());
                    alertSendingService.sendErrAlertsDirectly(
                            jobFlow, "There is already an active run: " + activeRun.getId());
                    return;
                }
            } else {
                var conflict =
                        flowRunBizService.findRunConflict(jobFlow.getId(), executionConfig, scheduleTime, flowRunId);
                if (conflict != null) {
                    if (flowRunId != null) {
                        jobFlowRunService.updateStatusFromTo(flowRunId, CREATED, FAILURE);
                    }
                    var activeRun = conflict.activeRun();
                    log.warn(
                            "Job flow {} rejected by {}, active run id: {}",
                            jobFlow.getId(),
                            conflict.type(),
                            activeRun.getId());
                    alertSendingService.sendErrAlertsDirectly(
                            jobFlow,
                            "Flow run rejected: "
                                    + conflict.type().getDescription()
                                    + ", active run: "
                                    + activeRun.getId());
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

            // Persisted as non-terminal: FlowRunDispatcher.drainAndExecute picks up SUBMITTED runs,
            // while WAITING ones stay parked until advanceWaitingRuns finds their dependencies satisfied.
            jobFlowRunService.saveOrUpdate(jobFlowRun);
            log.info(
                    "Job flow run: {} is created, job flow: {}, time: {}",
                    jobFlowRun.getId(),
                    code,
                    System.currentTimeMillis());
        }
    }

    private JobFlowDag createFlowFromConfig(ExecutionConfig executionConfig) {
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

    private Object getProcessLock(String code) {
        var newLock = new Object();
        var lock = PARALLEL_LOCK_MAP.putIfAbsent(code, newLock);
        if (lock == null) {
            lock = newLock;
        }
        return lock;
    }
}
