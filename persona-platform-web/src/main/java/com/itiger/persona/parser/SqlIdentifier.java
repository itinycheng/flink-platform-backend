package com.itiger.persona.parser;

import com.itiger.persona.constants.SqlConstant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import static com.itiger.persona.constants.SqlConstant.AS;
import static com.itiger.persona.constants.SqlConstant.DOT;
import static com.itiger.persona.constants.SqlConstant.EMPTY;
import static com.itiger.persona.constants.SqlConstant.SPACE;
import static com.itiger.persona.constants.SqlConstant.UNDERSCORE;

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
        String backTick = SqlConstant.BACK_TICK;
        return String.join(EMPTY, backTick, name, backTick);
    }

    public String toColumnString() {
        String backTick = SqlConstant.BACK_TICK;
        String quotedName = String.join(EMPTY, backTick, name, backTick);
        return String.join(DOT, qualifier, quotedName);
    }

    public String toColumnAsStatement() {
        return String.join(SPACE, toColumnString(), AS, newColumnName());
    }

    public String newColumnName() {
        return String.join(UNDERSCORE, qualifier.toLowerCase(), name);
    }
}
