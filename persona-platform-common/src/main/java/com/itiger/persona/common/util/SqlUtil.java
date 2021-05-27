package com.itiger.persona.common.util;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

import static com.itiger.persona.common.constants.JobConstant.SEMICOLON;
import static com.itiger.persona.common.constants.JobConstant.SQL_COMMENT_SYMBOL;
import static com.itiger.persona.common.constants.JobConstant.SQL_LINE_SEPARATOR;

/**
 * utils
 *
 * @author tiny.wang
 */
public class SqlUtil {

    /**
     * strip comments and ';'
     */
    public static String stripUselessCharsFromSql(String statement) {
        // delete comments
        String stmt = Arrays.stream(statement.split(SQL_LINE_SEPARATOR))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .filter(segment -> !segment.startsWith(SQL_COMMENT_SYMBOL))
                .collect(Collectors.joining(SQL_LINE_SEPARATOR));
        // delete ';'
        if (stmt.endsWith(SEMICOLON)) {
            stmt = stmt.substring(0, stmt.length() - 1).trim();
        }
        return stmt;

    }

}
