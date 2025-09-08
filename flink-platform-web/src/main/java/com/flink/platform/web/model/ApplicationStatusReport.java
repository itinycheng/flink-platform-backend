package com.flink.platform.web.model;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.web.util.YarnHelper;
import jakarta.annotation.Nonnull;
import lombok.Data;
import org.apache.hadoop.yarn.api.records.ApplicationReport;

import static com.flink.platform.common.util.Preconditions.checkNotNull;

@Data
public class ApplicationStatusReport {

    private final ExecutionStatus status;
    private final ApplicationReport report;

    public ApplicationStatusReport(@Nonnull ApplicationReport report) {
        this.report = checkNotNull(report);
        this.status = checkNotNull(YarnHelper.getStatus(report));
    }

    public boolean isTerminalState() {
        return status.isTerminalState();
    }

    public String getTrackingUrl() {
        return report.getTrackingUrl();
    }

    public long getStartTime() {
        return report.getStartTime();
    }

    public long getFinishTime() {
        return report.getFinishTime();
    }
}
