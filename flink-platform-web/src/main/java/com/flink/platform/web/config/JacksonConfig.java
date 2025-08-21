package com.flink.platform.web.config;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.MapperFeature.PROPAGATE_TRANSIENT_MARKER;

@Configuration
public class JacksonConfig {

    /**
     * Customize the SpringBoot's default Jackson ObjectMapper.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer customizeObjectMapper() {
        return builder -> {
            builder.featuresToEnable(PROPAGATE_TRANSIENT_MARKER);
            builder.featuresToDisable(FAIL_ON_UNKNOWN_PROPERTIES);
        };
    }
}
