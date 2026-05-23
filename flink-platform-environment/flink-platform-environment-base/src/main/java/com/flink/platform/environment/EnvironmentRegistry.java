package com.flink.platform.environment;

import com.flink.platform.common.environment.EnvironmentSpec;
import com.flink.platform.common.environment.EnvironmentType;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry indexing environment client holders by (type).
 */
@Slf4j
@Component
public class EnvironmentRegistry {

    private final Map<EnvironmentType, EnvironmentClientHolder<?>> holders = new ConcurrentHashMap<>();

    public <C> void register(EnvironmentSpec spec, EnvironmentClientFactory<C> factory) {
        var holder = new EnvironmentClientHolder<>(spec, factory);
        var existing = holders.putIfAbsent(spec.getType(), holder);
        if (existing != null) {
            throw new IllegalStateException("Environment " + spec + " already registered with factory "
                    + existing.getSpec() + ", cannot register duplicate with factory "
                    + factory.supportedType());
        }
        log.info("Registered environment: {}", spec);
    }

    @SuppressWarnings("unused")
    public void unregister(EnvironmentType type) {
        var holder = holders.remove(type);
        if (holder == null) {
            return;
        }

        holder.close();
        log.info("Unregistered environment: {}", type);
    }

    public boolean hasClient(EnvironmentType type) {
        return holders.containsKey(type);
    }

    @SuppressWarnings("unchecked")
    public <C> C getClient(EnvironmentType type) {
        var holder = holders.get(type);
        if (holder == null) {
            throw new IllegalStateException("No environment registered for type: " + type);
        }
        return (C) holder.get();
    }

    public List<EnvironmentSpec> specs() {
        return holders.values().stream().map(EnvironmentClientHolder::getSpec).toList();
    }

    public void checkHealth() {
        holders.values().parallelStream().filter(holder -> !holder.isHealthy()).forEach(holder -> {
            log.warn("Env client unhealthy, invalidating: {}", holder.getSpec());
            holder.invalidate();
        });
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down EnvironmentRegistry, closing {} holders", holders.size());
        holders.values().parallelStream().forEach(EnvironmentClientHolder::close);
        holders.clear();
    }
}
