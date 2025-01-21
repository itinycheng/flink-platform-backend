package com.flink.platform.web.command;

import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;

/** job command. */
@Data
public abstract class JobCommand {

    protected final long jobRunId;

    protected AbstractTask task;

    /**
     * Set a timeout for the job, only used to transfer value to the task.
     */
    protected Duration timeout;

    /**
     * Set a time to stop the job, leave it null if no need to monitor and kill the job.
     */
    protected LocalDateTime expectedStopTime;

    /**
     * build a command.
     *
     * @return command to execute.
     */
    public abstract String toCommandString();

    static class ExpectedStopTimeComparator implements Comparator<JobCommand> {

        @Override
        public int compare(JobCommand o1, JobCommand o2) {
            return o1.getExpectedStopTime().compareTo(o2.getExpectedStopTime());
        }
    }
}
