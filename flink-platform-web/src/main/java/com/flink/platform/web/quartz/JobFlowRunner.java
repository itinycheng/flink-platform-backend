package com.flink.platform.web.quartz;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.graph.DAG;
import com.flink.platform.common.model.JobEdge;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.service.JobFlowScheduleService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.getNonTerminals;
import static com.flink.platform.common.enums.JobFlowStatus.SCHEDULING;

/** submit job flow. */
@Slf4j
public class JobFlowRunner implements Job {

    private static final Map<String, Object> PARALLEL_LOCK_MAP = new ConcurrentHashMap<>();

    private final JobFlowService jobFlowService = SpringContext.getBean(JobFlowService.class);

    private final JobFlowRunService jobFlowRunService =
            SpringContext.getBean(JobFlowRunService.class);

    private final JobFlowScheduleService jobFlowScheduleService =
            SpringContext.getBean(JobFlowScheduleService.class);

    @Override
    public void execute(JobExecutionContext context) {
        JobDetail detail = context.getJobDetail();
        JobKey key = detail.getKey();
        String code = key.getName();

        synchronized (getProcessLock(code)) {

            // Get job flow info.
            JobFlow jobFlow =
                    jobFlowService.getOne(
                            new QueryWrapper<JobFlow>()
                                    .lambda()
                                    .eq(JobFlow::getCode, code)
                                    .in(JobFlow::getStatus, SCHEDULING));
            if (jobFlow == null) {
                log.warn("The job flow: {} isn't exists or not in scheduling status", code);
                return;
            }

            // Validate flow json.
            DAG<Long, JobVertex, JobEdge> flow = jobFlow.getFlow();
            if (flow == null || flow.getVertices().isEmpty()) {
                log.warn("The job flow: {} doesn't contain any vertices", jobFlow.getCode());
                return;
            }

            // Avoid preforming the same job flow multiple times at the same time.
            JobFlowRun jobFlowRun =
                    jobFlowRunService.getOne(
                            new QueryWrapper<JobFlowRun>()
                                    .lambda()
                                    .eq(JobFlowRun::getFlowId, jobFlow.getId())
                                    .in(JobFlowRun::getStatus, getNonTerminals()));
            if (jobFlowRun != null) {
                log.warn(
                        "The job flow:{} is in non-terminal status, run id: {}",
                        jobFlow.getId(),
                        jobFlowRun.getId());
            }

            // Create job flow run instance.
            jobFlowRun = new JobFlowRun();
            jobFlowRun.setFlowId(jobFlow.getId());
            jobFlowRun.setName(
                    String.join(
                            "-",
                            jobFlow.getName(),
                            jobFlow.getCode(),
                            String.valueOf(System.currentTimeMillis())));
            jobFlowRun.setFlowId(jobFlow.getId());
            jobFlowRun.setFlow(jobFlow.getFlow());
            jobFlowRun.setVersion(jobFlow.getVersion());
            jobFlowRun.setHost(Constant.HOST_IP);
            jobFlowRun.setPriority(jobFlow.getPriority());
            jobFlowRun.setReceivers(jobFlow.getReceivers());
            jobFlowRun.setStatus(SUBMITTED);
            jobFlowRunService.save(jobFlowRun);

            // register job flow run.
            jobFlowScheduleService.registerToScheduler(jobFlowRun);
            log.info(
                    "Job flow run: {} is created, job flow: {}, time: {}",
                    jobFlowRun.getFlowId(),
                    code,
                    System.currentTimeMillis());
        }
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
