package com.itiger.persona.common.job;

import java.util.Arrays;
import java.util.stream.Collectors;

import static com.itiger.persona.common.job.Constants.SEMICOLON;
import static com.itiger.persona.common.job.Constants.SQL_COMMENT_SYMBOL;
import static com.itiger.persona.common.job.Constants.SQL_LINE_SEPARATOR;

/**
 * utils
 *
 * @author tiny.wang
 */
public class Utils {

    /**
     * strip comments and ';'
     */
    public static String stripUselessCharsFromSql(String statement) {
        // delete comments
        String stmt = Arrays.stream(statement.split(SQL_LINE_SEPARATOR))
                .map(String::trim)
                .filter(segment -> !segment.startsWith(SQL_COMMENT_SYMBOL))
                .collect(Collectors.joining(SQL_LINE_SEPARATOR));
        // delete ';'
        if (stmt.endsWith(SEMICOLON)) {
            stmt = stmt.substring(0, statement.length() - 1).trim();
        }
        return stmt;

    }

}
