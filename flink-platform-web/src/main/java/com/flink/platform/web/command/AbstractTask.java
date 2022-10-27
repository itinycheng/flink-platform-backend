package com.flink.platform.web.command;

import lombok.Getter;

import java.util.UUID;

/** Abstract task for all types. */
@Getter
public abstract class AbstractTask {

    protected final String uuid;

    protected final long jobRunId;

    public AbstractTask(long jobRunId) {
        this.jobRunId = jobRunId;
        this.uuid = UUID.randomUUID().toString();
    }

    public abstract void run() throws Exception;

    public abstract void cancel();
}
