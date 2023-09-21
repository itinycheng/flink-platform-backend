package com.flink.platform.web.quartz;

import com.flink.platform.common.model.JobVertex;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.runner.JobExecuteThread;
import com.flink.platform.web.runner.JobResponse;
import com.flink.platform.web.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;

import java.util.concurrent.ThreadPoolExecutor;

import static com.flink.platform.common.enums.ExecutionCondition.AND;

/**
 * submit single job. <br>
 * TODO: handle the case that thread pool fully used.
 */
@Slf4j
public class JobRunner implements Job {

    public static final ThreadPoolExecutor EXECUTOR = ThreadUtil.newFixedThreadExecutor("JobRunnerExecutor", 100);

    private final WorkerConfig workerConfig = SpringContext.getBean(WorkerConfig.class);

    @Override
    public void execute(JobExecutionContext context) {
        JobDetail detail = context.getJobDetail();
        JobKey key = detail.getKey();
        Long jobId = Long.parseLong(key.getName());

        JobVertex jobVertex = new JobVertex(jobId, jobId);
        jobVertex.setPrecondition(AND);
        EXECUTOR.execute(() -> {
            JobExecuteThread callable = new JobExecuteThread(null, jobVertex, workerConfig);
            JobResponse response = callable.call();
            log.info("The job: {} is processed, result: {}", jobId, response);
        });
    }
}
