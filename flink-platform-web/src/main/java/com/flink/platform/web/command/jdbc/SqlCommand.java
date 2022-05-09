package com.flink.platform.web.command.jdbc;

import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.web.command.JobCommand;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** SQL command. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SqlCommand implements JobCommand {

    private Long dsId;

    private List<String> sqls;

    /** build a command. */
    @Override
    public String toCommandString() {
        return JsonUtil.toJsonString(sqls);
    }
}
