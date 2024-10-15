package com.flink.platform.web.command.sql;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.Datasource;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.web.command.JobCommand;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.flink.platform.common.enums.JobType.HIVE_SQL;

/** Hive SQL command executor. */
@Slf4j
@Component("hiveSqlCommandExecutor")
public class HiveSqlCommandExecutor extends SqlCommandExecutor {

    @Override
    public boolean isSupported(JobType jobType) {
        return jobType == HIVE_SQL;
    }

    @Nonnull
    @Override
    public JobCallback execCommand(@Nonnull JobCommand command) throws Exception {
        SqlCommand sqlCommand = (SqlCommand) command;
        Datasource datasource = datasourceService.getById(sqlCommand.getDsId());
        HiveSqlTask task = new HiveSqlTask(sqlCommand.getJobRunId(), sqlCommand.getSqls(), datasource);
        sqlCommand.setTask(task);
        task.run();

        return task.buildResult();
    }
}
