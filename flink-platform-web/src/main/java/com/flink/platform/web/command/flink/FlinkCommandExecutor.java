package com.flink.platform.web.command.flink;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.dao.entity.result.ShellCallback;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.AbstractTask;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.external.LocalHadoopService;
import com.flink.platform.web.util.YarnHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.constants.Constant.EMPTY;
import static com.flink.platform.common.constants.JobConstant.HADOOP_USER_NAME;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;

/** Flink command executor. */
@Slf4j
@Component("flinkCommandExecutor")
public class FlinkCommandExecutor implements CommandExecutor {

    private static final List<JobType> SUPPORTED_JOB_TYPES = Arrays.asList(JobType.FLINK_JAR, JobType.FLINK_SQL);

    @Value("${storage.username}")
    private String hadoopUser;

    @Autowired
    private WorkerConfig workerConfig;

    @Lazy
    @Autowired
    private LocalHadoopService localHadoopService;

    @Autowired
    private JobRunInfoService jobRunInfoService;

    @Override
    public boolean isSupported(JobType jobType) {
        return SUPPORTED_JOB_TYPES.contains(jobType);
    }

    @Nonnull
    @Override
    public JobCallback execCommand(@Nonnull JobCommand command) throws Exception {
        FlinkCommand flinkCommand = (FlinkCommand) command;
        FlinkYarnTask task = new FlinkYarnTask(
                flinkCommand.getJobRunId(),
                flinkCommand.getMode(),
                flinkCommand.toCommandString(),
                buildEnvProps(),
                workerConfig.getFlinkSubmitTimeoutMills());
        flinkCommand.setTask(task);
        task.run();

        String appId = task.getAppId();
        String jobId = task.getJobId();
        ShellCallback callback = task.buildShellCallback();

        // call `killCommand` method if execute command failed.
        if (!SUCCESS.equals(task.finalStatus())) {
            return new JobCallback(jobId, appId, null, callback, EMPTY, task.finalStatus());
        }

        // Get the application report from Hadoop Yarn.
        if (StringUtils.isNotEmpty(appId) && StringUtils.isNotEmpty(jobId)) {
            ExecutionStatus status = SUBMITTED;
            String trackingUrl = EMPTY;
            try {
                long jobRunId = command.getJobRunId();
                String applicationTag = YarnHelper.getApplicationTag(jobRunId);
                var statusReport = localHadoopService.getApplicationReport(applicationTag);
                status = statusReport.getStatus();
                trackingUrl = statusReport.getTrackingUrl();
            } catch (Exception e) {
                log.error("Failed to get ApplicationReport after command executed", e);
            }
            return new JobCallback(jobId, appId, trackingUrl, callback, EMPTY, status);
        }

        // If both appId and jobId are empty, means that there is no need to submit task to Yarn.
        if (StringUtils.isEmpty(appId) && StringUtils.isEmpty(jobId)) {
            return new JobCallback(jobId, appId, EMPTY, callback, EMPTY, SUCCESS);
        }

        // If one of appId and jobId is empty, means that there is a problem with the submitted task.
        return new JobCallback(jobId, appId, EMPTY, callback, EMPTY, FAILURE);
    }

    @Override
    public void killCommand(@Nonnull JobCommand command) {
        // Need provide processId, applicationId, deployMode.
        AbstractTask task = command.getTask();
        if (task == null) {
            JobRunInfo jobRun = jobRunInfoService.getById(command.getJobRunId());
            JobCallback callback = jobRun.getBackInfo();
            if (!jobRun.getStatus().isTerminalState() && callback != null) {
                FlinkYarnTask newTask = new FlinkYarnTask(jobRun.getId(), jobRun.getDeployMode());
                newTask.setProcessId(callback.getProcessId());
                newTask.setAppId(callback.getAppId());
                task = newTask;
            }
        }

        if (task != null) {
            task.cancel();
        }
    }

    private Map<String, String> buildEnvProps() {
        var map = new HashMap<String, String>(1);
        map.put(HADOOP_USER_NAME, hadoopUser);
        return map;
    }
}
