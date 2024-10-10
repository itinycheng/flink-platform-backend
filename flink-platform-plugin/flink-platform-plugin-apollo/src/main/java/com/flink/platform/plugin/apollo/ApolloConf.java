package com.flink.platform.plugin.apollo;

import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * apollo configuration.
 */
@Configuration
@EnableApolloConfig
public class ApolloConf {

    @Bean
    public DatasourceNamespaceBean datasourceNamespaceBean() {
        return new DatasourceNamespaceBean();
    }
}
