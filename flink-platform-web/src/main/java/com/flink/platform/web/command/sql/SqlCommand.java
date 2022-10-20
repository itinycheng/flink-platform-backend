package com.flink.platform.web.command.sql;

import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.web.command.JobCommand;
import lombok.Getter;

import java.util.List;

/** SQL command. */
@Getter
public class SqlCommand extends JobCommand {

    private final Long dsId;

    private final List<String> sqls;

    public SqlCommand(long jobRunId, Long dsId, List<String> sqls) {
        super(jobRunId);
        this.dsId = dsId;
        this.sqls = sqls;
    }

    /** build a command. */
    @Override
    public String toCommandString() {
        return JsonUtil.toJsonString(sqls);
    }
}
