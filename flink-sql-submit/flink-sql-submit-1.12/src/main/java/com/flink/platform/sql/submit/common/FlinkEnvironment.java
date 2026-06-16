package com.flink.platform.sql.submit.common;

import org.apache.flink.table.api.StatementSet;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.functions.UserDefinedFunction;

import com.flink.platform.common.exception.FlinkJobGenException;
import com.flink.platform.sql.submit.base.common.FlinkEnvAdapter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class FlinkEnvironment implements FlinkEnvAdapter {

    private final TableEnvironment tableEnv;

    private transient StatementSet pendingStatementSet;

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
        // Flink 1.12's pure TableEnvironment (BATCH) has no toDataStream; run SELECT eagerly,
        // mirroring the legacy executeSql().print() path.
        tableEnv.executeSql(sql).print();
    }

    @Override
    public void registerInsert(String insertSql) {
        if (pendingStatementSet == null) {
            pendingStatementSet = tableEnv.createStatementSet();
        }
        pendingStatementSet.addInsertSql(insertSql);
    }

    @Override
    public void execute() {
        if (pendingStatementSet != null) {
            try {
                pendingStatementSet.execute();
            } finally {
                pendingStatementSet = null;
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
