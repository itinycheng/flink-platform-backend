package com.flink.platform.web.command;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.web.enums.JobType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

import static com.flink.platform.web.enums.JobType.CLICKHOUSE_SQL;

/**
 * @author tiny.wang
 */
@Slf4j
@DS("clickhouse")
@Component("clickhouseCommandExecutor")
public class ClickhouseCommandExecutor implements CommandExecutor {

    @Resource
    private JdbcTemplate clickhouseJdbcTemplate;

    @Override
    public boolean isSupported(JobType jobType) {
        return jobType == CLICKHOUSE_SQL;
    }

    @Override
    public JobCallback execCommand(String command) {
        List<String> exceptionMessages = new ArrayList<>();
        JsonUtil.toList(command).forEach(sql -> {
            try {
                log.info("exec clickhouse sql: {}", sql);
                clickhouseJdbcTemplate.execute(sql);
            } catch (Exception e) {
                exceptionMessages.add(e.getMessage());
                log.error("Execute clickhouse sql: {} failed.", sql, e);
            }
        });

        return new JobCallback(null, null,
                exceptionMessages.isEmpty() ? "success" : JsonUtil.toJsonString(exceptionMessages));
    }
}
