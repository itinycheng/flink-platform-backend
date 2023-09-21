package com.flink.platform.web.command.sql;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.Datasource;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.dao.service.DatasourceService;
import com.flink.platform.web.command.AbstractTask;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCommand;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.flink.platform.common.enums.JobType.CLICKHOUSE_SQL;
import static com.flink.platform.common.enums.JobType.HIVE_SQL;
import static com.flink.platform.common.enums.JobType.MYSQL_SQL;

/** SQL command executor. */
@Slf4j
@Component("sqlCommandExecutor")
public class SqlCommandExecutor implements CommandExecutor {

    @Autowired
    private DatasourceService datasourceService;

    @Override
    public boolean isSupported(JobType jobType) {
        return jobType == CLICKHOUSE_SQL || jobType == MYSQL_SQL || jobType == HIVE_SQL;
    }

    @Nonnull
    @Override
    public JobCallback execCommand(@Nonnull JobCommand command) throws Exception {
        SqlCommand sqlCommand = (SqlCommand) command;
        Datasource datasource = datasourceService.getById(sqlCommand.getDsId());
        SqlTask task = new SqlTask(sqlCommand.getJobRunId(), sqlCommand.getSqls(), datasource);
        sqlCommand.setTask(task);
        task.run();

        return task.buildResult();
    }

    @Override
    public void killCommand(@Nonnull JobCommand command) {
        AbstractTask task = command.getTask();
        if (task != null) {
            task.cancel();
        }
    }
}
