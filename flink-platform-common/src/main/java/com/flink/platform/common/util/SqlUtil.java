package com.flink.platform.common.util;

import com.flink.platform.common.constants.Constant;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static com.flink.platform.common.constants.Constant.SEMICOLON;
import static com.flink.platform.common.constants.JobConstant.LIMIT_PATTERN;
import static com.flink.platform.common.constants.JobConstant.READ_MAX_ROWS;
import static com.flink.platform.common.constants.JobConstant.SQL_COMMENT_SYMBOL;
import static com.flink.platform.common.constants.JobConstant.SQL_LINE_SEPARATOR;

/** utils. */
public class SqlUtil {

    /** strip comments and semicolon. */
    public static String stripUselessCharsFromSql(String statement) {
        // delete comments
        String stmt =
                Arrays.stream(statement.split(SQL_LINE_SEPARATOR))
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

    public static String limitRowNum(final String sqlQuery) {
        String sql = sqlQuery.trim();
        Matcher matcher = LIMIT_PATTERN.matcher(sql);
        if (matcher.find()) {
            String rows = matcher.group("num");
            int currentRows = Integer.parseInt(rows);
            if (currentRows > READ_MAX_ROWS) {
                sql = sql.substring(0, sql.length() - rows.length()) + READ_MAX_ROWS;
            }
        } else {
            sql = sql + " LIMIT " + READ_MAX_ROWS;
        }
        return sql;
    }
}
