package com.flink.platform.web.command.condition;

import com.flink.platform.web.command.JobCommand;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** condition command. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConditionCommand implements JobCommand {

    private boolean success;

    @Override
    public String toCommandString() {
        return "dp nothing";
    }
}
