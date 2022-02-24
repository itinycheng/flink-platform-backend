package com.flink.platform.web.command;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.service.JobRunInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.common.enums.JobType.CLICKHOUSE_SQL;

/** Clickhouse command executor. */
@Slf4j
@DS("clickhouse")
@Component("clickhouseCommandExecutor")
public class ClickhouseCommandExecutor implements CommandExecutor {

    @Autowired private JdbcTemplate clickhouseJdbcTemplate;

    @Autowired private JobRunInfoService jobRunInfoService;

    @Override
    public boolean isSupported(JobType jobType) {
        return jobType == CLICKHOUSE_SQL;
    }

    @Override
    public JobCallback execCommand(String command) {
        List<String> exceptionMessages = new ArrayList<>();
        JsonUtil.toList(command)
                .forEach(
                        sql -> {
                            try {
                                log.info("exec clickhouse sql: {}", sql);
                                clickhouseJdbcTemplate.execute(sql);
                            } catch (Exception e) {
                                exceptionMessages.add(e.getMessage());
                                log.error("Execute clickhouse sql: {} failed.", sql, e);
                            }
                        });

        boolean isSucceed = exceptionMessages.isEmpty();
        return new JobCallback(
                null,
                null,
                isSucceed ? "success" : JsonUtil.toJsonString(exceptionMessages),
                isSucceed ? SUCCESS : FAILURE);
    }
}
