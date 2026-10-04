package com.flink.platform.web.dto.request;

import com.flink.platform.common.enums.JobFlowType;
import com.flink.platform.dao.entity.Dependency;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.config.WorkerConfig;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;
import java.util.stream.Collectors;

import static com.flink.platform.common.enums.ExpiryPolicy.FIXED_DURATION;
import static com.flink.platform.common.enums.JobFlowType.JOB_FLOW;
import static com.flink.platform.common.enums.JobFlowType.JOB_LIST;
import static com.flink.platform.web.quartz.QuartzUtil.hasValidScheduleInterval;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/** Job flow request info. */
@Getter
@NoArgsConstructor
public class JobFlowRequest {

    private static final Set<JobFlowType> VALID_TYPE = Set.of(JOB_FLOW, JOB_LIST);

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

        msg = verifyMaxJobsPerRun();
        if (msg != null) {
            return msg;
        }

        msg = verifyMaxActiveRuns();
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

        msg = verifyMaxJobsPerRun();
        if (msg != null) {
            return msg;
        }

        msg = verifyMaxActiveRuns();
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
        var alerts = getAlerts();
        if (isEmpty(alerts)) {
            return null;
        }

        for (var alert : getAlerts()) {
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

        var statusesRequired = precondition.requiresStatusMatched();
        for (var dependency : dependsOn.getDependencies()) {
            if (dependency == null) {
                return "A dependency isn't null";
            }

            var flowId = dependency.getFlowId();
            if (flowId == null) {
                return "The flowId of a dependency isn't null";
            }

            if (flowId.equals(getId())) {
                return "A workflow can't depend on itself";
            }

            if (statusesRequired && isEmpty(dependency.getStatuses())) {
                return "The statuses of a dependency can't be empty when precondition is " + precondition;
            }

            var expiry = dependency.getExpiry();
            if (expiry == null) {
                return "The expiry of a dependency isn't null";
            }

            if (expiry == FIXED_DURATION) {
                if (!dependency.hasValidDuration()) {
                    return String.format("The duration of %s is invalid", expiry);
                }
            } else if (isNotBlank(dependency.getDuration())) {
                return String.format("The duration of %s should not be set", expiry);
            }
        }

        // TODO: Decide whether flow dependencies must be acyclic.
        var flowIds =
                dependsOn.getDependencies().stream().map(Dependency::getFlowId).collect(Collectors.toSet());
        var existing = resolveJobFlowService().listExistingFlowIds(flowIds);
        var missing = flowIds.stream().filter(id -> !existing.contains(id)).toList();
        if (CollectionUtils.isNotEmpty(missing)) {
            return "Dependency workflow not found: " + missing;
        }

        return null;
    }

    public String verifyCronExpr() {
        try {
            var cronExpr = getCronExpr();
            if (isNotBlank(cronExpr) && !hasValidScheduleInterval(cronExpr)) {
                return "Quartz schedule interval must bigger than 1 minute";
            }
            return null;
        } catch (Exception e) {
            return "Quartz cron parsing failed";
        }
    }

    public String verifyMaxJobsPerRun() {
        var config = getConfig();
        if (config == null) {
            return null;
        }

        var maxThreads = resolveWorkerConfig().getMaxPerFlowExecThreads();
        var max = config.getMaxJobsPerRun();
        if (max < 1 || max > maxThreads) {
            return "The range of maxJobsPerRun is 1 ~ " + maxThreads;
        }
        return null;
    }

    public String verifyMaxActiveRuns() {
        var config = getConfig();
        if (config == null) {
            return null;
        }

        var maxActiveRuns = config.getMaxActiveRuns();
        var maxActiveRunsPerFlow = resolveWorkerConfig().getMaxActiveRunsPerFlow();
        if (maxActiveRuns < 1 || maxActiveRuns > maxActiveRunsPerFlow) {
            return "The range of maxActiveRuns is 1 ~ " + maxActiveRunsPerFlow;
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

    private WorkerConfig resolveWorkerConfig() {
        return SpringContext.getBean(WorkerConfig.class);
    }

    private JobFlowService resolveJobFlowService() {
        return SpringContext.getBean(JobFlowService.class);
    }
}
