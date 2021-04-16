package com.itiger.persona.flink.helper;

import com.itiger.persona.common.job.SqlCommand;
import org.apache.flink.table.api.TableEnvironment;

import java.util.List;

/**
 * exec sqls in the same statement
 *
 * @author tiny.wang
 */
public class ExecuteSqls {

    public static void execSqls(TableEnvironment tEnv, List<SqlCommand> sqlCommands) {
        sqlCommands.forEach(ExecuteSqls::executeSql);
    }

    private static void executeSql(SqlCommand sqlCommand) {

    }
}
