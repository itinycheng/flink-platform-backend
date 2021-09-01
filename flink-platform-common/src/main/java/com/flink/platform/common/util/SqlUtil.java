package com.flink.platform.common.util;

import com.flink.platform.common.constants.Constant;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

import static com.flink.platform.common.constants.Constant.SEMICOLON;
import static com.flink.platform.common.constants.JobConstant.SQL_COMMENT_SYMBOL;
import static com.flink.platform.common.constants.JobConstant.SQL_LINE_SEPARATOR;

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
                .collect(Collectors.joining(Constant.SPACE + SQL_LINE_SEPARATOR));
        // delete ';'
        if (stmt.endsWith(SEMICOLON)) {
            stmt = stmt.substring(0, stmt.length() - 1).trim();
        }
        return stmt;

    }

}
