package com.flink.platform.web.command.subflow;

import com.flink.platform.web.command.JobCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * Sub flow command.
 */
@Getter
@Setter
public class FlowCommand extends JobCommand {

    private long flowId;

    public FlowCommand(long jobRunId, long flowId) {
        super(jobRunId);
        this.flowId = flowId;
    }

    @Override
    public String toCommandString() {
        return "";
    }
}
