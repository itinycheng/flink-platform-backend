package com.flink.platform.common.job;

import com.flink.platform.common.enums.SqlType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.flink.platform.common.enums.SqlType.SET;

/** sql command. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Sql {

    private SqlType type;

    private String[] operands;

    public String toSqlString() {
        if (type == SET) {
            return "SET " + String.join("=", operands[0], operands[1]);
        } else {
            return operands[0];
        }
    }
}
