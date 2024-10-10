package com.flink.platform.web.service;

import com.flink.platform.web.service.plugin.ApolloService;
import jakarta.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class PluginService {

    @Lazy
    @Autowired
    private ApolloService apolloService;

    public String getApolloConfig(@Nonnull String namespace, @Nonnull String key) {
        return apolloService.getConfig(namespace, key);
    }
}
