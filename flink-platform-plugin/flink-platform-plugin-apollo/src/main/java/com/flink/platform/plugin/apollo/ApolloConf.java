package com.flink.platform.plugin.apollo;

import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * apollo configuration.
 */
@Configuration
@EnableApolloConfig
public class ApolloConf {

    private static final String CONFIG = "META-INF/app.properties";

    private static final String SALT = "apollo.salt";

    private final String secretKey;

    public ApolloConf() {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(CONFIG)) {
            if (input == null) {
                throw new IOException("No apollo config file found, path: " + CONFIG);
            }

            Properties properties = new Properties();
            properties.load(input);
            this.secretKey = properties.getProperty(SALT);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + CONFIG, e);
        }
    }

    @Bean
    public DatasourceNamespaceBean datasourceNamespaceBean() {
        return new DatasourceNamespaceBean(secretKey);
    }
}
