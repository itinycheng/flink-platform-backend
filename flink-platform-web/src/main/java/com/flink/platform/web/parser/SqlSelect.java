package com.flink.platform.web.parser;

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
public class SqlSelect {

    private List<SqlIdentifier> selectList;

    private SqlIdentifier from;

    private SqlWhere where;

    private SqlOrderBy orderBy;

    private SqlLimit limit;
}
