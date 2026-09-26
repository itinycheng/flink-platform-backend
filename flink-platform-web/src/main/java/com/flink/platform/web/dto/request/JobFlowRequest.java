package com.flink.platform.web.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flink.platform.common.enums.JobFlowType;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.alert.AlertConfig;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.quartz.QuartzUtil;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Set;

import static com.flink.platform.common.enums.ExecutionCondition.ALL_MATCHED;
import static com.flink.platform.common.enums.ExecutionCondition.ANY_MATCHED;
import static com.flink.platform.common.enums.ExpiryPolicy.FIXED_DURATION;
import static com.flink.platform.common.enums.JobFlowType.JOB_FLOW;
import static com.flink.platform.common.enums.JobFlowType.JOB_LIST;

/** Job flow request info. */
@Getter
@NoArgsConstructor
public class JobFlowRequest {

    private static final Set<JobFlowType> VALID_TYPE = Set.of(JOB_FLOW, JOB_LIST);

    private static WorkerConfig workerConfig_;

    @Delegate
    private final JobFlow jobFlow = new JobFlow();

    public String validateOnCreate() {
        var msg = verifyName();
        if (msg != null) {
            return msg;
        }

        msg = verifyType();
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

        msg = verifyAlerts();
        if (msg != null) {
            return msg;
        }

        msg = verifyDependsOn();
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

        msg = verifyType();
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

        msg = verifyDependsOn();
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

    public String verifyType() {
        String errorMsg = null;
        var type = getType();
        if (type != null && !VALID_TYPE.contains(type)) {
            errorMsg = "The type of Job flow isn't valid, the valid types are " + VALID_TYPE;
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

    @SuppressWarnings("D")
    public String verifyDependsOn() {
        var dependsOn = getDependsOn();
        if (dependsOn == null || !dependsOn.hasDependencies()) {
            return null;
        }

        var precondition = dependsOn.getPrecondition();
        if (precondition == null) {
            return "The precondition of Job flow dependencies isn't null";
        }

        var statusesRequired = precondition == ALL_MATCHED || precondition == ANY_MATCHED;
        for (var item : dependsOn.getDependencies()) {
            var flowId = item.getFlowId();
            if (flowId == null) {
                return "The flowId of a dependent item isn't null";
            }

            if (flowId.equals(getId())) {
                return "A workflow can't depend on itself";
            }

            if (statusesRequired && CollectionUtils.isEmpty(item.getStatuses())) {
                return "The statuses of a dependent item can't be empty when precondition is " + precondition;
            }

            var expiry = item.getExpiry();
            if (expiry == null) {
                return "The expiry of a dependent item isn't null";
            }

            if (expiry == FIXED_DURATION) {
                if (!item.hasValidDuration()) {
                    return String.format("The duration of %s is invalid", expiry);
                }
            } else if (StringUtils.isNotBlank(item.getDuration())) {
                return String.format("The duration of %s should not be set", expiry);
            }
        }

        return null;
    }

    public String verifyCronExpr() {
        try {
            var cronExpr = getCronExpr();
            if (StringUtils.isNotBlank(cronExpr) && !QuartzUtil.hasValidScheduleInterval(cronExpr)) {
                return "Quartz schedule interval must bigger than 1 minute";
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
