package com.flink.platform.sql.submit.base.helper;

import com.flink.platform.common.enums.SqlType;
import com.flink.platform.common.exception.FlinkJobGenException;
import com.flink.platform.common.job.Sql;
import com.flink.platform.sql.submit.base.common.FlinkEnvAdapter;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.flink.platform.common.enums.SqlType.INSERT_INTO;
import static com.flink.platform.common.enums.SqlType.INSERT_OVERWRITE;

/** Exec sql list in order. */
public class ExecuteSqls {

    private static final Set<SqlType> INSERT_TYPES = EnumSet.of(INSERT_INTO, INSERT_OVERWRITE);

    public static void execSQLs(FlinkEnvAdapter env, List<Sql> sqls) throws Exception {
        if (!env.supportsMixedSelectInsert()) {
            rejectMixedSelectAndInsert(sqls);
        }

        for (Sql sql : sqls) {
            switch (sql.getType()) {
                case INSERT_INTO:
                case INSERT_OVERWRITE:
                    env.registerInsert(sql.getOperands()[0]);
                    break;
                case SELECT:
                    env.registerSelect(sql.getOperands()[0]);
                    break;
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
                    env.executeAndPrint(sql.getOperands()[0]);
                    break;
                default:
                    throw new FlinkJobGenException(String.format("Unknown sql type, sql: %s", sql));
            }
        }
        env.execute();
    }

    private static void rejectMixedSelectAndInsert(List<Sql> sqls) {
        boolean hasSelect = false;
        boolean hasInsert = false;
        for (Sql sql : sqls) {
            SqlType type = sql.getType();
            hasSelect |= (type == SqlType.SELECT);
            hasInsert |= INSERT_TYPES.contains(type);
            if (hasSelect && hasInsert) {
                throw new FlinkJobGenException("SELECT and INSERT cannot coexist in one submission: "
                        + "SELECT triggers env.execute() and INSERT triggers StatementSet.execute(), "
                        + "which submit two separate Flink jobs. Split them into two job runs.");
            }
        }
    }
}
