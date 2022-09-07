package com.flink.platform.web.util;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.exception.JobStatusScrapeException;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.FinalApplicationStatus;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;

/** yarn util. */
public class YarnHelper {

    public static ExecutionStatus getStatus(ApplicationReport applicationReport) {
        FinalApplicationStatus finalStatus = applicationReport.getFinalApplicationStatus();
        switch (finalStatus) {
            case UNDEFINED:
                return getNonFinalStatus(applicationReport);
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

    private static ExecutionStatus getNonFinalStatus(ApplicationReport applicationReport) {
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
}
