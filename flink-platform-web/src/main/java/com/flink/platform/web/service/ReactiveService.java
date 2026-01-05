package com.flink.platform.web.service;

import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.exception.UnrecoverableException;
import com.flink.platform.common.util.ExceptionUtil;
import com.flink.platform.common.util.SqlUtil;
import com.flink.platform.dao.entity.Datasource;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.entity.vo.ReactiveDataVo;
import com.flink.platform.web.entity.vo.ReactiveExecVo;
import com.flink.platform.web.util.CollectLogRunnable;
import com.flink.platform.web.util.CommandUtil;
import com.flink.platform.web.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BiConsumer;

import static com.flink.platform.web.util.JdbcUtil.createConnection;
import static com.flink.platform.web.util.JdbcUtil.toJavaObject;

/** Manage datasource service. */
@Slf4j
@Service
public class ReactiveService {

    private final WorkerConfig workerConfig;

    private final ThreadPoolExecutor executor;

    private final Map<String, BlockingQueue<String>> cmdOutputBufferMap;

    private final List<CommandBuilder> commandBuilders;

    private final JobRunExtraService jobRunExtraService;

    @Autowired
    public ReactiveService(
            WorkerConfig workerConfig, List<CommandBuilder> commandBuilders, JobRunExtraService jobRunExtraService) {
        this.workerConfig = workerConfig;
        this.commandBuilders = commandBuilders;
        this.jobRunExtraService = jobRunExtraService;
        this.executor = ThreadUtil.newDaemonFixedThreadExecutor("Reactive-job", workerConfig.getReactiveExecThreads());
        this.cmdOutputBufferMap = new ConcurrentHashMap<>();
    }

    public ReactiveExecVo execFlink(String execId, JobInfo jobInfo, String[] envProps) throws Exception {
        var jobRun = jobRunExtraService.createFrom(jobInfo, null, Constant.HOST_IP);
        jobRun.setId(0L);
        var command = commandBuilders.stream()
                .filter(builder -> builder.isSupported(jobInfo.getType(), jobInfo.getVersion()))
                .findFirst()
                .orElseThrow(() -> new UnrecoverableException("No available job command builder"))
                .buildCommand(null, jobRun)
                .toCommandString();

        CompletableFuture.runAsync(
                        () -> {
                            try {
                                CommandUtil.exec(
                                        command,
                                        envProps,
                                        workerConfig.getFlinkSubmitTimeoutMills(),
                                        collectCmdResult(execId));
                            } catch (Exception e) {
                                cmdOutputBufferMap.get(execId).add(ExceptionUtil.stackTrace(e));
                            }
                        },
                        executor)
                .whenComplete((unused, throwable) -> {
                    try {
                        ThreadUtil.sleep(5000);
                    } finally {
                        cmdOutputBufferMap.remove(execId);
                    }
                });

        return new ReactiveExecVo(execId);
    }

    public ReactiveDataVo execSql(String execId, JobInfo jobInfo, Datasource datasource) throws Exception {
        var sqls = SqlUtil.convertToSqls(jobInfo.getSubject());
        if (sqls.size() != 1) {
            throw new RuntimeException("Only one sql can be executed at a time");
        }

        var statement = sqls.getFirst().toSqlString();
        try (var connection = createConnection(datasource.getType(), datasource.getParams());
                var stmt = connection.createStatement()) {
            String[] columnNames;
            var dataList = new ArrayList<Object[]>();
            if (stmt.execute(statement)) {
                try (var resultSet = stmt.getResultSet()) {
                    var metaData = resultSet.getMetaData();
                    // metadata.
                    var num = metaData.getColumnCount();
                    columnNames = new String[num];
                    for (var i = 1; i <= num; i++) {
                        columnNames[i - 1] = metaData.getColumnName(i);
                    }

                    // data list.
                    var dbType = datasource.getType();
                    while (resultSet.next()) {
                        var item = new Object[num];
                        for (var i = 1; i <= num; i++) {
                            item[i - 1] = toJavaObject(resultSet.getObject(i), dbType);
                        }
                        dataList.add(item);
                    }
                }
            } else {
                columnNames = new String[] {"success"};
                dataList.add(new Object[] {false});
            }

            return new ReactiveDataVo(execId, columnNames, dataList, null);
        }
    }

    public boolean bufferExists(String execId) {
        return cmdOutputBufferMap.containsKey(execId);
    }

    public List<String> getBufferByExecId(String execId) {
        var printLogQueue = cmdOutputBufferMap.get(execId);
        if (printLogQueue != null) {
            var cmdLogs = new ArrayList<String>();
            printLogQueue.drainTo(cmdLogs);
            return cmdLogs;
        } else {
            return null;
        }
    }

    private BiConsumer<CollectLogRunnable.CmdOutType, String> collectCmdResult(String execId) {
        var cmdLogQueue = new ArrayBlockingQueue<String>(50_000);
        cmdOutputBufferMap.put(execId, cmdLogQueue);
        return (cmdOutType, line) -> {
            try {
                switch (cmdOutType) {
                    case STD:
                    case ERR:
                        cmdLogQueue.offer(line);
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                log.error("Consumer command log failed", e);
            }
        };
    }
}
