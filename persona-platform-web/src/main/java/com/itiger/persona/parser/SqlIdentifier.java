package com.itiger.persona.parser;

import com.itiger.persona.common.constants.Constant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

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

    private String name;

    public String toTableString() {
        return String.join(SPACE, name, qualifier);
    }

    public String toSimpleColumnStatement() {
        String backTick = Constant.BACK_TICK;
        return String.join(EMPTY, backTick, name, backTick);
    }

    public String toColumnStatement() {
        String backTick = Constant.BACK_TICK;
        String quotedName = String.join(EMPTY, backTick, name, backTick);
        return String.join(DOT, qualifier, quotedName);
    }

    public String toColumnAsStatement() {
        return String.join(SPACE, toColumnStatement(), AS, newColumnName());
    }

    public String newColumnName() {
        return String.join(UNDERSCORE, qualifier.toLowerCase(), name);
    }

    public String correctColumnName(boolean isNew) {
        if (isNew) {
            return this.newColumnName();
        } else {
            return this.getName();
        }
    }
}
