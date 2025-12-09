package com.flink.platform.web.entity.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flink.platform.common.util.DateUtil;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.alert.AlertConfig;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.config.WorkerConfig;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.quartz.CronExpression;

import java.util.Date;
import java.util.List;

/** Job flow request info. */
@Getter
@NoArgsConstructor
public class JobFlowRequest {

    private static WorkerConfig workerConfig_;

    @Delegate
    private final JobFlow jobFlow = new JobFlow();

    public String validateOnCreate() {
        var msg = verifyName();
        if (msg != null) {
            return msg;
        }

        msg = verifyParallelism();
        if (msg != null) {
            return msg;
        }

        msg = verifyParams();
        if (msg != null) {
            return msg;
        }

        return verifyCronExpr();
    }

    public String validateOnUpdate() {
        var msg = verifyId();
        if (msg != null) {
            return msg;
        }

        msg = verifyAlerts();
        if (msg != null) {
            return msg;
        }

        msg = verifyCronExpr();
        if (msg != null) {
            return msg;
        }

        msg = verifyParallelism();
        if (msg != null) {
            return msg;
        }

        msg = verifyParams();
        if (msg != null) {
            return msg;
        }

        return verifyFlow();
    }

    public String verifyId() {
        String errorMsg = null;
        if (getId() == null) {
            errorMsg = "The id of Job flow isn't null";
        }
        return errorMsg;
    }

    public String verifyName() {
        String errorMsg = null;
        if (getName() == null) {
            errorMsg = "The name of Job flow isn't null";
        }
        return errorMsg;
    }

    public String verifyFlow() {
        if (getFlow() == null) {
            return null;
        }

        String errorMsg = null;
        if (!getFlow().isValid()) {
            errorMsg = "The flow graph is invalid";
        }
        return errorMsg;
    }

    public String verifyAlerts() {
        List<AlertConfig> alerts = getAlerts();
        if (CollectionUtils.isEmpty(alerts)) {
            return null;
        }

        for (AlertConfig alert : getAlerts()) {
            if (alert.getAlertId() == null) {
                return "The alert id is null";
            }
        }
        return null;
    }

    public String verifyCronExpr() {
        try {
            var cronExpr = getCronExpr();
            if (StringUtils.isNotBlank(cronExpr)) {
                CronExpression cronExpression = new CronExpression(cronExpr);
                Date validTime1 = cronExpression.getNextValidTimeAfter(new Date());
                Date validTime2 = cronExpression.getNextValidTimeAfter(validTime1);
                if (validTime2.getTime() - validTime1.getTime() < DateUtil.MILLIS_PER_MINUTE) {
                    return "Quartz schedule interval must bigger than 1 minute";
                }
            }

            return null;
        } catch (Exception e) {
            return "Quartz cron parsing failed";
        }
    }

    public String verifyParallelism() {
        var config = getConfig();
        if (config == null) {
            return null;
        }

        WorkerConfig workerConfig = getOrInitWorkerConfig();
        int maxThreads = workerConfig.getMaxPerFlowExecThreads();
        int parallelism = config.getParallelism();
        if (parallelism < 1 || parallelism > maxThreads) {
            return "The range of parallelism is 1 ~ " + maxThreads;
        }

        return null;
    }

    public String verifyParams() {
        var params = getParams();
        if (params == null) {
            return null;
        }

        if (params.keySet().stream().anyMatch(StringUtils::isBlank)) {
            return "Param key can't be empty";
        }

        return null;
    }

    public void setConfig(ExecutionConfig config) {
        if (config == null) {
            config = new ExecutionConfig();
        }

        if (config.getParallelism() < 1) {
            var workerConfig = getOrInitWorkerConfig();
            config.setParallelism(workerConfig.getPerFlowExecThreads());
        }
        this.getJobFlow().setConfig(config);
    }

    @JsonIgnore
    public WorkerConfig getOrInitWorkerConfig() {
        if (workerConfig_ == null) {
            workerConfig_ = SpringContext.getBean(WorkerConfig.class);
        }

        return workerConfig_;
    }
}
