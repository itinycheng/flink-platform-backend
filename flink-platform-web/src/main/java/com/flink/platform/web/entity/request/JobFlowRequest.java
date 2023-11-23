package com.flink.platform.web.entity.request;

import com.flink.platform.common.util.DateUtil;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.alert.AlertConfig;
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

    @Delegate
    private final JobFlow jobFlow = new JobFlow();

    public String validateOnCreate() {
        String msg = verifyName();
        if (msg != null) {
            return msg;
        }

        return verifyCronExpr();
    }

    public String validateOnUpdate() {
        String msg = verifyId();
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
            String cronExpr = getCronExpr();
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
}
