package com.flink.platform.web.command.sql;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.exception.CommandUnableGenException;
import com.flink.platform.common.job.Sql;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.SqlJob;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.JobCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.flink.platform.common.enums.JobType.CLICKHOUSE_SQL;
import static com.flink.platform.common.util.SqlUtil.convertToSqls;

/** SQL command builder. */
@Slf4j
@Component("sqlCommandBuilder")
public class SqlCommandBuilder implements CommandBuilder {

    @Override
    public boolean isSupported(JobType jobType, String version) {
        return jobType == CLICKHOUSE_SQL;
    }

    @Override
    public JobCommand buildCommand(Long flowRunId, JobRunInfo jobRunInfo) {
        SqlJob sqlJob = jobRunInfo.getConfig().unwrap(SqlJob.class);
        if (sqlJob == null) {
            throw new CommandUnableGenException("Invalid job config.");
        }

        List<String> sqlList = new ArrayList<>();
        for (Sql sql : convertToSqls(jobRunInfo.getSubject())) {
            sqlList.add(sql.toSqlString());
        }

        if (sqlList.size() == 0) {
            throw new CommandUnableGenException(
                    String.format(
                            "No available sql or parsing failed, subject: %s",
                            jobRunInfo.getSubject()));
        }

        return new SqlCommand(sqlJob.getDsId(), sqlList);
    }
}
