package com.flink.platform.sql.submit.base.common;

/**
 * Version-agnostic facade over a Flink table environment.
 */
public interface FlinkEnvAdapter {

    void setConfig(String key, String value);

    void createTemporarySystemFunction(String name, String className);

    void createTemporaryFunction(String name, String className);

    /**
     * Run a non-SELECT, non-INSERT SQL synchronously and print its result. <br> Suitable for
     * DDL/USE/SHOW/EXPLAIN and catalog-creation statements.
     */
    void executeAndPrint(String sql);

    /**
     * Register a SELECT to be executed on {@link #execute()}. Implementations typically attach a
     * print sink without triggering execution yet. May be called once per submission.
     */
    void registerSelect(String sql);

    /**
     * Register an INSERT to be executed on {@link #execute()}. Implementations typically grow an
     * internal {@code StatementSet}, lazily initialized on first call.
     */
    void registerInsert(String insertSql);

    /**
     * Trigger any pending work registered through {@link #registerSelect}/{@link #registerInsert}.
     * Should be called exactly once at the end of a submission.
     */
    void execute() throws Exception;

    /**
     * Whether this version can run SELECT and INSERT in the same submission as one Flink job (e.g.
     * via {@code StatementSet.attachAsDataStream()} on Flink 1.14+). When {@code false},
     * {@code ExecuteSqls} rejects submissions that mix the two to avoid two independent
     * {@code execute()} calls.
     */
    default boolean supportsMixedSelectInsert() {
        return false;
    }
}
