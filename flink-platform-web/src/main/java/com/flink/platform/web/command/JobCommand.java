package com.flink.platform.web.command;

import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;

/** job command. */
@Data
public abstract class JobCommand implements Comparable<JobCommand> {

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
     * Compare the expected stop time first, then compare the job run id.
     */
    @Override
    public int compareTo(JobCommand o) {
        LocalDateTime t1 = this.expectedStopTime;
        LocalDateTime t2 = o.getExpectedStopTime();
        if (t1 == null && t2 != null) {
            return 1;
        }

        if (t1 != null && t2 == null) {
            return -1;
        }

        int cmp = t1 == t2 ? 0 : t1.compareTo(t2);
        return cmp == 0 ? Long.compare(this.jobRunId, o.getJobRunId()) : cmp;
    }

    /**
     * build a command.
     *
     * @return command to execute.
     */
    public abstract String toCommandString();
}
