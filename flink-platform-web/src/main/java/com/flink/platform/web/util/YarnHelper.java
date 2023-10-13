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
        return switch (finalStatus) {
            case UNDEFINED -> getNonFinalStatus(applicationReport);
            case FAILED -> ExecutionStatus.FAILURE;
            case KILLED -> ExecutionStatus.KILLED;
            case SUCCEEDED -> ExecutionStatus.SUCCESS;
            case ENDED -> ExecutionStatus.ABNORMAL;
        };
    }

    private static ExecutionStatus getNonFinalStatus(ApplicationReport applicationReport) {
        YarnApplicationState yarnState = applicationReport.getYarnApplicationState();
        return switch (yarnState) {
            case NEW, NEW_SAVING, SUBMITTED, ACCEPTED -> ExecutionStatus.SUBMITTED;
            case RUNNING -> ExecutionStatus.RUNNING;
            default -> throw new JobStatusScrapeException("Unrecognized nonFinal status: " + yarnState);
        };
    }
}
