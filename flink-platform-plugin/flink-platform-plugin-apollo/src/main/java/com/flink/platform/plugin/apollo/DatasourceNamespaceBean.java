package com.flink.platform.plugin.apollo;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.ctrip.framework.apollo.spring.annotation.ApolloConfig;
import com.ctrip.framework.apollo.spring.annotation.ApolloConfigChangeListener;
import com.flink.platform.plugin.apollo.util.JasyptUtil;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@Slf4j
public class DatasourceNamespaceBean implements NamespaceBean {

    private static final String NAMESPACE = "datasource";

    private final String password;

    @ApolloConfig(NAMESPACE)
    private Config config;

    public DatasourceNamespaceBean(String password) {
        this.password = password;
    }

    @ApolloConfigChangeListener(NAMESPACE)
    private void applicationOnChange(ConfigChangeEvent changeEvent) {
        Set<String> changedKeys = changeEvent.changedKeys();
        for (String changedKey : changedKeys) {
            log.info("apollo config changed: {}", changeEvent.getChange(changedKey));
        }
    }

    @Nonnull
    @Override
    public String namespace() {
        return NAMESPACE;
    }

    @Override
    public String getConfig(String key) {
        String value = config.getProperty(key, null);
        return JasyptUtil.isJasyptValue(value) ? JasyptUtil.decrypt(value, password) : value;
    }
}
