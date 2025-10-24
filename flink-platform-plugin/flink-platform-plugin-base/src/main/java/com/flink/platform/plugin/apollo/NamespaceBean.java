package com.flink.platform.plugin.apollo;

import jakarta.annotation.Nonnull;

/**
 * namespace data.
 */
public interface NamespaceBean {

    @Nonnull
    String namespace();

    String getConfig(String key);
}
