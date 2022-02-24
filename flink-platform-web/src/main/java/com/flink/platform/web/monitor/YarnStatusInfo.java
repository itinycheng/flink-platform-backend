package com.flink.platform.web.monitor;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.exception.JobStatusScrapeException;
import com.flink.platform.common.util.Preconditions;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.FinalApplicationStatus;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;

import java.time.Instant;
import java.time.LocalDateTime;

import static com.flink.platform.common.util.DateUtil.DEFAULT_ZONE_ID;

/** Yarn status info. */
public class YarnStatusInfo implements StatusInfo {

    private final ApplicationReport applicationReport;

    public YarnStatusInfo(ApplicationReport applicationReport) {
        this.applicationReport = Preconditions.checkNotNull(applicationReport);
    }

    @Override
    public ExecutionStatus getStatus() {
        FinalApplicationStatus finalStatus = applicationReport.getFinalApplicationStatus();
        switch (finalStatus) {
            case UNDEFINED:
                return getNonFinalStatus();
            case FAILED:
                return ExecutionStatus.FAILURE;
            case KILLED:
                return ExecutionStatus.KILLED;
            case SUCCEEDED:
                return ExecutionStatus.SUCCESS;
            case ENDED:
                return ExecutionStatus.ABNORMAL;
            default:
                throw new JobStatusScrapeException("Unrecognized final status: " + finalStatus);
        }
    }

    private ExecutionStatus getNonFinalStatus() {
        YarnApplicationState yarnState = applicationReport.getYarnApplicationState();
        switch (yarnState) {
            case NEW:
            case NEW_SAVING:
            case SUBMITTED:
            case ACCEPTED:
                return ExecutionStatus.SUBMITTED;
            case RUNNING:
                return ExecutionStatus.RUNNING;
            default:
                throw new JobStatusScrapeException("Unrecognized nonFinal status: " + yarnState);
        }
    }

    @Override
    public LocalDateTime getStartTime() {
        long startTime = applicationReport.getStartTime();
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), DEFAULT_ZONE_ID);
    }

    @Override
    public LocalDateTime getEndTime() {
        long finishTime = applicationReport.getFinishTime();
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(finishTime), DEFAULT_ZONE_ID);
    }
}
