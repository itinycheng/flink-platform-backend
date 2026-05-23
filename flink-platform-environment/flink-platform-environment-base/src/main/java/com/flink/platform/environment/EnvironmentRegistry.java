package com.flink.platform.environment;

import com.flink.platform.common.environment.EnvironmentSpec;
import com.flink.platform.common.environment.EnvironmentType;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry indexing environment client holders by (type, name).
 */
@Slf4j
@Component
public class EnvironmentRegistry {

    private final Map<EnvironmentSpec, EnvironmentClientHolder<?>> holders = new ConcurrentHashMap<>();

    public <C> void register(EnvironmentSpec spec, EnvironmentClientFactory<C> factory) {
        var holder = new EnvironmentClientHolder<>(spec, factory);
        var existing = holders.putIfAbsent(spec, holder);
        if (existing != null) {
            throw new IllegalStateException("Environment " + spec + " already registered with factory "
                    + existing.getSpec() + ", cannot register duplicate with factory "
                    + factory.supportedType());
        }
        log.info("Registered environment: {}", spec);
    }

    @SuppressWarnings("unused")
    public void unregister(EnvironmentSpec spec) {
        var holder = holders.remove(spec);
        if (holder == null) {
            log.warn("Environment {} unregistered", spec);
            return;
        }

        holder.close();
        log.info("Unregistered environment: {}", spec);
    }

    @SuppressWarnings("unchecked")
    public <C> C find(EnvironmentType type) {
        for (var holder : holders.values()) {
            if (type.equals(holder.getSpec().getType())) {
                return (C) holder.get();
            }
        }

        throw new IllegalStateException("No environment registered for type: " + type);
    }

    public List<EnvironmentSpec> registered() {
        return new ArrayList<>(holders.keySet());
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
