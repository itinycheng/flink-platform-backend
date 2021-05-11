package com.itiger.persona.parser;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author tiny.wang
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SqlIdentifier {

    private String qualifier;

    private String name;

    public String toTableString() {
        return String.join(" ", name, qualifier);
    }

    public String toColumnString() {
        return String.join(".", qualifier, name);
    }
}
