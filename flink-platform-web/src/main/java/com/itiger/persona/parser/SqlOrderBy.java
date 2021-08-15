package com.itiger.persona.parser;

import com.itiger.persona.enums.SqlExpression;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author tiny.wang
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SqlOrderBy {

    private SqlExpression type;

    private List<SqlIdentifier> items;

}
