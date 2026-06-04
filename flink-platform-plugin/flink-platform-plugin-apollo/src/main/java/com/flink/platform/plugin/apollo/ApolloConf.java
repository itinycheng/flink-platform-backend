package com.flink.platform.plugin.apollo;

import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Apollo configuration. Bootstrap properties are imported from {@code apollo-<profile>.properties}
 * via {@code spring.config.import} in {@code application.yml}; Apollo's
 * {@code ApolloApplicationContextInitializer} promotes the standard keys (app.id, apollo.meta,
 * apollo.cluster, apollo.cache-dir, env) into system properties before {@code ConfigService}
 * initializes.
 */
@Slf4j
@Configuration
@EnableApolloConfig({}) // empty: no namespace eagerly loaded.
public class ApolloConf {

    private final String secretKey;

    public ApolloConf(@Value("${apollo.salt}") String secretKey) {
        this.secretKey = secretKey;
    }

    @Bean
    @Lazy
    public DatasourceNamespaceBean datasourceNamespaceBean() {
        log.info("Initializing datasource namespace bean (lazy), salt loaded: {}", StringUtils.isNotBlank(secretKey));
        return new DatasourceNamespaceBean(secretKey);
    }
}
