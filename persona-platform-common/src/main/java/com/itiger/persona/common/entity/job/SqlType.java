package com.itiger.persona.common.entity.job;

import com.itiger.persona.common.constants.JobConstant;
import com.itiger.persona.common.exception.FlinkJobGenException;
import com.itiger.persona.common.util.SqlUtil;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * sql command type
 *
 * @author tiny.wang
 */
public enum SqlType {

    /**
     * sql type enum
     */
    SELECT("(SELECT.*)",
            (operands) -> Optional.of(new String[]{operands[0]})),

    INSERT_INTO("(INSERT\\s+INTO.*)",
            (operands) -> Optional.of(new String[]{operands[0]})),

    INSERT_OVERWRITE("(INSERT\\s+OVERWRITE.*)",
            (operands) -> Optional.of(new String[]{operands[0]})),

    USE("(USE\\s+(?!CATALOG)(.*))",
            (operands) -> Optional.of(new String[]{operands[0]})),

    USE_CATALOG("(USE\\s+CATALOG.*)",
            (operands) -> Optional.of(new String[]{operands[0]})),

    CREATE_CATALOG("(CREATE\\s+CATALOG.*)",
            (operands) -> Optional.of(new String[]{operands[0]})),

    CREATE_DATABASE("(CREATE\\s+DATABASE.*)",
            (operands) -> Optional.of(new String[]{operands[0]})),

    CREATE_TABLE("(CREATE\\s+TABLE.*)",
            (operands) -> Optional.of(new String[]{operands[0]})),

    CREATE_VIEW("(CREATE\\s+(TEMPORARY)?\\s+VIEW.*)",
            (operands) -> Optional.of(new String[]{operands[0]})),

    CREATE_FUNCTION("(CREATE\\s+(TEMPORARY|TEMPORARY\\s+SYSTEM)?\\s+FUNCTION.*)",
            (operands) -> Optional.of(new String[]{operands[0]})),

    DROP_DATABASE("(DROP\\s+DATABASE.*)",
            (operands) -> Optional.of(new String[]{operands[0]})),

    DROP_TABLE("(DROP\\s+TABLE.*)",
            (operands) -> Optional.of(new String[]{operands[0]})),

    DROP_VIEW("(DROP\\s+(TEMPORARY)?\\s+VIEW.*)",
            (operands) -> Optional.of(new String[]{operands[0]})),

    DROP_FUNCTION("(DROP\\s+(TEMPORARY|TEMPORARY\\s+SYSTEM)?\\s+FUNCTION.*)",
            (operands) -> Optional.of(new String[]{operands[0]})),

    ALTER_DATABASE("(ALTER\\s+DATABASE.*)",
            (operands) -> Optional.of(new String[]{operands[0]})),

    ALTER_TABLE("(ALTER\\s+TABLE.*)",
            (operands) -> Optional.of(new String[]{operands[0]})),

    ALTER_FUNCTION("(ALTER\\s+(TEMPORARY|TEMPORARY\\s+SYSTEM)?\\s+FUNCTION.*)",
            (operands) -> Optional.of(new String[]{operands[0]})),

    SHOW_CATALOGS("SHOW\\s+CATALOGS",
            (operands) -> Optional.of(new String[]{"SHOW CATALOGS"})),

    SHOW_DATABASES("SHOW\\s+DATABASES",
            (operands) -> Optional.of(new String[]{"SHOW DATABASES"})),

    SHOW_TABLES("SHOW\\s+TABLES",
            (operands) -> Optional.of(new String[]{"SHOW TABLES"})),

    SHOW_FUNCTIONS("SHOW\\s+FUNCTIONS",
            (operands) -> Optional.of(new String[]{"SHOW FUNCTIONS"})),

    SHOW_MODULES("SHOW\\s+MODULES",
            (operands) -> Optional.of(new String[]{"SHOW MODULES"})),

    DESCRIBE("(DESCRIBE.*)",
            (operands) -> Optional.of(new String[]{operands[0]})),

    EXPLAIN("(EXPLAIN\\s+PLAN\\s+FOR.*)",
            (operands) -> Optional.of(new String[]{operands[0]})),

    SET("SET\\s+(\\S+)\\s*=\\s*(.*)",
            (operands) -> {
                final int len = 2;
                if (operands.length == len) {
                    return Optional.of(new String[]{operands[0], operands[1]});
                } else {
                    throw new FlinkJobGenException(String.format("parse set statement failed, operands: %s",
                            Arrays.toString(operands)));
                }
            });

    public final Pattern pattern;

    public final Function<String[], Optional<String[]>> operandConverter;

    SqlType(String regex, Function<String[], Optional<String[]>> operandConverter) {
        this.pattern = Pattern.compile(regex, JobConstant.SQL_PATTERN_CONFIGS);
        this.operandConverter = operandConverter;
    }

    public static Sql parse(String statement) {
        // delete the comment and semicolon at the end of sql
        String stmt = SqlUtil.stripUselessCharsFromSql(statement);
        // parse sql
        for (SqlType type : values()) {
            Matcher matcher = type.pattern.matcher(stmt);
            if (matcher.matches()) {
                return type.operandConverter.apply(Stream.iterate(0, i -> i + 1)
                        .limit(matcher.groupCount())
                        .map(matcher::group)
                        .toArray(String[]::new))
                        .map((operands) -> new Sql(type, operands))
                        .orElseThrow(() -> new FlinkJobGenException(
                                String.format("cannot match a correct sql statement: %s", stmt)));
            }
        }
        throw new FlinkJobGenException(String.format("cannot parse statement: %s", stmt));
    }

}
