package com.itiger.persona.parser;

import com.itiger.persona.common.constants.Constant;
import com.itiger.persona.common.util.Preconditions;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import static com.itiger.persona.common.constants.Constant.AS;
import static com.itiger.persona.common.constants.Constant.DOT;
import static com.itiger.persona.common.constants.Constant.EMPTY;
import static com.itiger.persona.common.constants.Constant.SPACE;
import static com.itiger.persona.common.constants.Constant.UNDERSCORE;

/**
 * @author tiny.wang
 */
@Data
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class SqlIdentifier {

    private String qualifier;

    private String[] names;

    public String toTableString() {
        return String.join(SPACE, names[0], qualifier);
    }

    public String toSimpleColumnStatement() {
        String backTick = Constant.BACK_TICK;
        return String.join(EMPTY, backTick, names[0], backTick);
    }

    public String toColumnStatement() {
        String backTick = Constant.BACK_TICK;
        String quotedName = String.join(EMPTY, backTick, names[0], backTick);
        return String.join(DOT, qualifier, quotedName);
    }

    public String toColumnAsStatement() {
        return String.join(SPACE, toColumnStatement(), AS, newColumnName());
    }

    public String newColumnName() {
        return String.join(UNDERSCORE, qualifier.toLowerCase(), names[0]);
    }

    public String correctColumnName(boolean isNew) {
        if (isNew) {
            return newColumnName();
        } else {
            return getName();
        }
    }

    public String getName() {
        return this.names[0];
    }

    public static SqlIdentifier of(String qualifier, String... names) {
        Preconditions.checkThrow(StringUtils.isBlank(qualifier),
                () -> new RuntimeException("qualifier is null"));
        Preconditions.checkThrow(names == null || names.length == 0,
                () -> new RuntimeException("qualifier is null"));
        return new SqlIdentifier(qualifier, names);
    }
}
