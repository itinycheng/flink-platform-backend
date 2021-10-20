package com.flink.platform.web.parser;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.util.Preconditions;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import static com.flink.platform.common.constants.Constant.AS;
import static com.flink.platform.common.constants.Constant.DOT;
import static com.flink.platform.common.constants.Constant.EMPTY;
import static com.flink.platform.common.constants.Constant.SPACE;
import static com.flink.platform.common.constants.Constant.UNDERSCORE;

/** Sql identifier. */
@Data
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class SqlIdentifier {

    private String qualifier;

    private String[] names;

    public String toSimpleColumnStatement() {
        String backTick = Constant.BACK_TICK;
        return String.join(EMPTY, backTick, names[0], backTick);
    }

    public String toColumnStatement() {
        String quotedName = toSimpleColumnStatement();
        return String.join(DOT, qualifier.toLowerCase(), quotedName);
    }

    public String toColumnAsStatement() {
        return String.join(SPACE, toColumnStatement(), AS, newColumnName());
    }

    public String newColumnName() {
        return String.join(UNDERSCORE, qualifier.toLowerCase(), names[0]);
    }

    @JsonIgnore
    public String getName() {
        return this.names[0];
    }

    public static SqlIdentifier of(String qualifier, String... names) {
        Preconditions.checkThrow(
                StringUtils.isBlank(qualifier), () -> new RuntimeException("qualifier is null"));
        Preconditions.checkThrow(
                names == null || names.length == 0,
                () -> new RuntimeException("qualifier is null"));
        return new SqlIdentifier(qualifier, names);
    }
}
