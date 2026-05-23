package com.flink.platform.environment;

import com.flink.platform.common.environment.EnvironmentSpec;
import com.flink.platform.common.environment.EnvironmentType;

/**
 * Factory creating long-lived clients for a specific environment type.
 */
public interface EnvironmentClientFactory<C> {

    EnvironmentType supportedType();

    /**
     * Build a client from the given spec.
     */
    C create(EnvironmentSpec spec) throws Exception;

    /** Release the underlying resources held by the client. */
    default void close(C client) throws Exception {
        if (client instanceof AutoCloseable c) {
            c.close();
        }
    }

    /** Light-weight liveness check. */
    default boolean healthy(C client) {
        return true;
    }
}
