package com.flink.platform.web.parser;

import com.flink.platform.web.enums.SqlExpression;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Simple sql where. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SimpleSqlWhere extends SqlWhere {

    private SqlExpression operator;

    private SqlIdentifier column;

    private String[] operands;
}
