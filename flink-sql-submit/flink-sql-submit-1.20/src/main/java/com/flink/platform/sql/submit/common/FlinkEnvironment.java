package com.flink.platform.sql.submit.common;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.PrintSink;
import org.apache.flink.table.api.StatementSet;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.functions.UserDefinedFunction;

import com.flink.platform.common.exception.FlinkJobGenException;
import com.flink.platform.sql.submit.base.common.FlinkEnvAdapter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class FlinkEnvironment implements FlinkEnvAdapter {

    private final StreamExecutionEnvironment env;

    private final StreamTableEnvironment tableEnv;

    private transient StatementSet pendingStatementSet;

    private transient boolean hasRegisteredSelect;

    @Override
    public void setConfig(String key, String value) {
        tableEnv.getConfig().getConfiguration().setString(key, value);
    }

    @Override
    public void createTemporarySystemFunction(String name, String className) {
        tableEnv.createTemporarySystemFunction(name, loadClass(className));
    }

    @Override
    public void createTemporaryFunction(String name, String className) {
        tableEnv.createTemporaryFunction(name, loadClass(className));
    }

    @Override
    public void executeAndPrint(String sql) {
        tableEnv.executeSql(sql).print();
    }

    @Override
    public void registerSelect(String sql) {
        Table table = tableEnv.sqlQuery(sql);
        tableEnv.toDataStream(table).sinkTo(new PrintSink<>());
        hasRegisteredSelect = true;
    }

    @Override
    public void registerInsert(String insertSql) {
        if (pendingStatementSet == null) {
            pendingStatementSet = tableEnv.createStatementSet();
        }
        pendingStatementSet.addInsertSql(insertSql);
    }

    @Override
    public void execute() throws Exception {
        if (pendingStatementSet != null) {
            try {
                pendingStatementSet.execute();
            } finally {
                pendingStatementSet = null;
            }
        }
        if (hasRegisteredSelect) {
            try {
                env.execute();
            } finally {
                hasRegisteredSelect = false;
            }
        }
    }

    private static Class<? extends UserDefinedFunction> loadClass(String className) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            return Class.forName(className, true, classLoader).asSubclass(UserDefinedFunction.class);
        } catch (Exception e) {
            throw new FlinkJobGenException("cannot load function class: " + className, e);
        }
    }
}
