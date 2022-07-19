package com.flink.platform.web.command.dependent;

import com.flink.platform.web.command.JobCommand;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** condition command. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DependentCommand implements JobCommand {

    /** whether the dependent verification is successful. */
    private boolean success;

    @Override
    public String toCommandString() {
        return "do nothing";
    }
}
