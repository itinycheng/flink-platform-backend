package com.flink.platform.plugin.apollo;

import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Apollo configuration. Bootstrap properties are imported from {@code apollo-<profile>.properties}
 * via {@code spring.config.import} in {@code application.yml}; Apollo's
 * {@code ApolloApplicationContextInitializer} promotes the standard keys (app.id, apollo.meta,
 * apollo.cluster, apollo.cache-dir, env) into system properties before {@code ConfigService}
 * initializes.
 */
@Configuration
@EnableApolloConfig
public class ApolloConf {

    private final String secretKey;

    public ApolloConf(@Value("${apollo.salt}") String secretKey) {
        this.secretKey = secretKey;
    }

    @Bean
    public DatasourceNamespaceBean datasourceNamespaceBean() {
        return new DatasourceNamespaceBean(secretKey);
    }
}
