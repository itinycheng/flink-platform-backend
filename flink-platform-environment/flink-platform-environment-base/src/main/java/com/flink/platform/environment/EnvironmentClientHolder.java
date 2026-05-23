package com.flink.platform.environment;

import com.flink.platform.common.environment.EnvironmentSpec;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Holds a single environment instance with lazy client initialization.
 */
@Slf4j
@Getter
public class EnvironmentClientHolder<C> implements AutoCloseable {

    private final EnvironmentSpec spec;
    private final EnvironmentClientFactory<C> factory;
    private final Object lock = new Object();

    @Nullable
    private volatile C client;

    public EnvironmentClientHolder(EnvironmentSpec spec, EnvironmentClientFactory<C> factory) {
        if (!spec.getType().equals(factory.supportedType())) {
            throw new IllegalArgumentException("Factory type " + factory.supportedType() + " mismatches spec: " + spec);
        }

        this.spec = spec;
        this.factory = factory;
    }

    public C get() {
        var tmp = client;
        if (tmp != null) {
            return tmp;
        }

        synchronized (lock) {
            tmp = client;
            if (tmp != null) {
                return tmp;
            }

            try {
                tmp = factory.create(spec);
                log.info("Lazy inited env client: {}", spec);
            } catch (Exception e) {
                throw new RuntimeException("Create env client failed, spec: " + spec, e);
            }

            client = tmp;
            return tmp;
        }
    }

    public void invalidate() {
        synchronized (lock) {
            var c = client;
            if (c == null) {
                return;
            }

            try {
                factory.close(c);
            } catch (Exception e) {
                log.warn("Close env client failed, spec: {}", spec, e);
            }

            client = null;
        }
    }

    public boolean isHealthy() {
        var c = client;
        if (c == null) {
            return true;
        }

        try {
            return factory.healthy(c);
        } catch (Exception e) {
            log.warn("Health check failed, spec: {}", spec, e);
            return false;
        }
    }

    @Override
    public void close() {
        invalidate();
    }
}
