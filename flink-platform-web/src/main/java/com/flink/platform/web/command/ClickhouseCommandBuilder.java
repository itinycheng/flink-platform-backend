package com.flink.platform.web.command;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.enums.SqlType;
import com.flink.platform.common.exception.JobCommandGenException;
import com.flink.platform.common.job.Sql;
import com.flink.platform.dao.entity.JobInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;

import static com.flink.platform.common.enums.JobType.CLICKHOUSE_SQL;

/** Clickhouse command builder. */
@Slf4j
@Component("clickhouseCommandBuilder")
public class ClickhouseCommandBuilder implements CommandBuilder {

    @Resource(name = "sqlContextHelper")
    private SqlContextHelper sqlContextHelper;

    @Override
    public boolean isSupported(JobType jobType, String version) {
        return jobType == CLICKHOUSE_SQL;
    }

    @Override
    public JobCommand buildCommand(JobInfo jobInfo) {
        List<String> sqlList = new ArrayList<>();
        for (Sql sql : sqlContextHelper.toSqls(jobInfo.getSubject())) {
            if (SqlType.SELECT == sql.getType()) {
                throw new JobCommandGenException(
                        "Clickhouse command builder doesn't support select statement.");
            }
            sqlList.add(sql.getOperands()[0]);
        }
        return new ClickhouseCommand(sqlList);
    }
}
