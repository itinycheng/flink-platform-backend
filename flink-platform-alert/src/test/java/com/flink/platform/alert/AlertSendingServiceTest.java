package com.flink.platform.alert;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.alert.AlertConfig;
import com.flink.platform.dao.entity.alert.AlertConfigList;
import com.flink.platform.dao.service.JobFlowService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AlertSendingServiceTest {

    private final AlertSender alertSender = mock(AlertSender.class);
    private final JobFlowService jobFlowService = mock(JobFlowService.class);
    private final AlertSendingService service = new AlertSendingService(alertSender, jobFlowService);

    private static JobFlowRun runWith(ExecutionStatus status, ExecutionStatus... triggers) {
        var alert = new AlertConfig();
        alert.setAlertId(7L);
        if (triggers.length > 0) {
            alert.setStatuses(List.of(triggers));
        }
        var alerts = new AlertConfigList();
        alerts.add(alert);
        var run = new JobFlowRun();
        run.setAlerts(alerts);
        run.setStatus(status);
        return run;
    }

    @Test
    void completionNotSentWhenFinalStatusNotTriggered() {
        // final SUCCESS, trigger only FAILURE -> not sent.
        service.sendAlerts(runWith(SUCCESS, FAILURE));
        verifyNoInteractions(alertSender);
    }

    @Test
    void completionSentWhenFinalStatusTriggered() {
        service.sendAlerts(runWith(FAILURE, FAILURE));
        verify(alertSender).sendAlert(eq(7L), any(JobFlowRun.class), any());
    }

    @Test
    void eventAlertBypassesTriggerStatus() {
        // run still RUNNING, trigger FAILURE -> sent regardless, because it's an event alert.
        service.sendAlertsDirectly(runWith(RUNNING, FAILURE), "execution timeout");
        verify(alertSender).sendAlert(eq(7L), any(JobFlowRun.class), eq("execution timeout"));
    }
}
