package com.flink.platform.web.parser;

import java.util.List;

/** Sql insert. */
public class SqlInsert {

    private boolean overwrite;

    private SqlIdentifier table;

    private List<SqlIdentifier> partitions;
}
