package com.flink.platform.sql.submit.common;

import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.StatementSet;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.functions.UserDefinedFunction;
import org.apache.flink.types.Row;

import lombok.Data;
import lombok.Getter;

@Data
@Getter
public class FlinkEnvironment {

    private final StreamExecutionEnvironment env;

    private final StreamTableEnvironment tableEnv;

    public TableResult executeSql(String sql) {
        return tableEnv.executeSql(sql);
    }

    public StatementSet createStatementSet() {
        return tableEnv.createStatementSet();
    }

    public Table sqlQuery(String query) {
        return tableEnv.sqlQuery(query);
    }

    public DataStream<Row> toDataStream(Table table) {
        return tableEnv.toDataStream(table);
    }

    public JobExecutionResult execute() throws Exception {
        return env.execute();
    }

    public void createTemporarySystemFunction(String name, Class<? extends UserDefinedFunction> functionClass) {
        tableEnv.createTemporarySystemFunction(name, functionClass);
    }

    public void createTemporaryFunction(String path, Class<? extends UserDefinedFunction> functionClass) {
        tableEnv.createTemporaryFunction(path, functionClass);
    }
}
