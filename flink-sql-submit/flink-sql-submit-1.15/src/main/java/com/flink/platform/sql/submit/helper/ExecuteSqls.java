package com.flink.platform.sql.submit.helper;

import org.apache.flink.table.api.StatementSet;
import org.apache.flink.table.api.TableEnvironment;

import com.flink.platform.common.enums.SqlType;
import com.flink.platform.common.exception.FlinkJobGenException;
import com.flink.platform.common.job.Sql;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.flink.platform.common.enums.SqlType.INSERT_INTO;
import static com.flink.platform.common.enums.SqlType.INSERT_OVERWRITE;
import static java.util.stream.Collectors.toSet;

/** Exec sql list in order. */
public class ExecuteSqls {

    private static final Set<SqlType> INSERT_TYPES =
            Stream.of(INSERT_INTO, INSERT_OVERWRITE).collect(toSet());

    public static void execSqls(TableEnvironment tEnv, List<Sql> sqls) {
        StatementSet statementSet = tEnv.createStatementSet();
        sqls.forEach(sql -> ExecuteSqls.executeSql(tEnv, statementSet, sql));
        if (sqls.stream().anyMatch(sql -> INSERT_TYPES.contains(sql.getType()))) {
            statementSet.execute();
        }
    }

    private static void executeSql(TableEnvironment tEnv, StatementSet statementSet, Sql sql) {
        switch (sql.getType()) {
            case SET:
                String[] operands = sql.getOperands();
                Configurations.setConfig(tEnv, operands[0], operands[1]);
                break;
            case INSERT_INTO:
            case INSERT_OVERWRITE:
                statementSet.addInsertSql(sql.getOperands()[0]);
                break;
            case SELECT:
            case USE:
            case USE_CATALOG:
            case CREATE_CATALOG:
            case CREATE_DATABASE:
            case CREATE_TABLE:
            case CREATE_VIEW:
            case CREATE_FUNCTION:
            case DROP_DATABASE:
            case DROP_TABLE:
            case DROP_VIEW:
            case DROP_FUNCTION:
            case ALTER_DATABASE:
            case ALTER_TABLE:
            case ALTER_FUNCTION:
            case SHOW_CATALOGS:
            case SHOW_DATABASES:
            case SHOW_FUNCTIONS:
            case SHOW_MODULES:
            case SHOW_TABLES:
            case DESCRIBE:
            case EXPLAIN:
                tEnv.executeSql(sql.getOperands()[0]).print();
                break;
            default:
                throw new FlinkJobGenException(String.format("Unknown sql type, sql: %s", sql));
        }
    }
}
