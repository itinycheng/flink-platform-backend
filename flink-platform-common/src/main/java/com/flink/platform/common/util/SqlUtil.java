package com.flink.platform.common.util;

import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.enums.SqlType;
import com.flink.platform.common.job.Sql;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static com.flink.platform.common.constants.Constant.SEMICOLON;
import static com.flink.platform.common.constants.JobConstant.LIMIT_PATTERN;
import static com.flink.platform.common.constants.JobConstant.READ_MAX_ROWS;
import static com.flink.platform.common.constants.JobConstant.SQL_COMMENT_SYMBOL;
import static com.flink.platform.common.constants.JobConstant.SQL_LINE_SEPARATOR;
import static com.flink.platform.common.constants.JobConstant.SQL_PATTERN;

/** utils. */
public class SqlUtil {

    public static List<Sql> convertToSqls(String statements) {
        statements = statements.trim();
        if (!statements.endsWith(SEMICOLON)) {
            statements = statements + SEMICOLON;
        }
        List<Sql> sqlList = new ArrayList<>();
        Matcher matcher = SQL_PATTERN.matcher(statements);
        while (matcher.find()) {
            String statement = matcher.group();
            sqlList.add(SqlType.parse(statement));
        }
        return sqlList;
    }

    /** strip comments and semicolon. */
    public static String stripUselessCharsFromSql(String statement) {
        // delete comments
        String stmt = Arrays.stream(statement.split(SQL_LINE_SEPARATOR))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .filter(segment -> !segment.startsWith(SQL_COMMENT_SYMBOL))
                .collect(Collectors.joining(Constant.SPACE + SQL_LINE_SEPARATOR));
        // delete ';'
        if (stmt.endsWith(SEMICOLON)) {
            stmt = stmt.substring(0, stmt.length() - 1).trim();
        }
        return stmt;
    }

    /** Limit return rows of query statement. */
    public static String limitRowNum(final String sqlQuery) {
        String sql = stripUselessCharsFromSql(sqlQuery);
        Matcher matcher = LIMIT_PATTERN.matcher(sql);
        if (matcher.find()) {
            String strNum1 = matcher.group("num1");
            String strNum2 = matcher.group("num2");
            int rows, length;
            if (StringUtils.isNotBlank(strNum2)) {
                rows = Integer.parseInt(strNum2);
                length = strNum2.length();
            } else {
                rows = Integer.parseInt(strNum1);
                length = strNum1.length();
            }
            if (rows > READ_MAX_ROWS) {
                sql = sql.substring(0, sql.length() - length) + READ_MAX_ROWS;
            }
        } else {
            sql = sql + " LIMIT " + READ_MAX_ROWS;
        }
        return sql;
    }
}
