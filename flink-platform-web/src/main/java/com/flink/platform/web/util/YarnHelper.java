package com.flink.platform.web.util;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.exception.JobStatusScrapeException;
import org.apache.hadoop.yarn.api.records.ApplicationReport;

/** yarn util. */
public class YarnHelper {

    private static final String APP_TAG_FORMAT = "job_run_id_%s";

    public static String getApplicationTag(Long jobRunId) {
        return String.format(APP_TAG_FORMAT, jobRunId);
    }

    public static ExecutionStatus getStatus(ApplicationReport applicationReport) {
        var finalStatus = applicationReport.getFinalApplicationStatus();
        return switch (finalStatus) {
            case UNDEFINED -> getNonFinalStatus(applicationReport);
            case FAILED -> ExecutionStatus.FAILURE;
            case KILLED -> ExecutionStatus.KILLED;
            case SUCCEEDED -> ExecutionStatus.SUCCESS;
            case ENDED -> ExecutionStatus.ABNORMAL;
        };
    }

    private static ExecutionStatus getNonFinalStatus(ApplicationReport applicationReport) {
        var yarnState = applicationReport.getYarnApplicationState();
        return switch (yarnState) {
            case NEW, NEW_SAVING, SUBMITTED, ACCEPTED -> ExecutionStatus.SUBMITTED;
            case RUNNING -> ExecutionStatus.RUNNING;
            default -> throw new JobStatusScrapeException("Unrecognized nonFinal status: " + yarnState);
        };
    }
}
