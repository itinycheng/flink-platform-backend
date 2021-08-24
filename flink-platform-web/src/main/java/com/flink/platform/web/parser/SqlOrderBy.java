package com.flink.platform.web.parser;

import com.flink.platform.web.enums.SqlExpression;
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
