package com.flink.platform.web.config;

import com.fasterxml.jackson.databind.Module;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static com.flink.platform.common.util.JsonUtil.defaultGlobalFeatures;
import static com.flink.platform.common.util.JsonUtil.defaultGlobalModules;
import static com.flink.platform.common.util.JsonUtil.defaultGlobalTimeZone;

@Configuration
public class JacksonConfig {

    /**
     * Customize the SpringBoot's default Jackson ObjectMapper.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer customizeObjectMapper() {
        return builder -> {
            builder.timeZone(defaultGlobalTimeZone());
            // Register global features.
            addGlobalFeaturesTo(builder);
            // Register global modules.
            var modules = defaultGlobalModules();
            builder.modulesToInstall(modules.toArray(new Module[0]));
        };
    }

    private void addGlobalFeaturesTo(Jackson2ObjectMapperBuilder builder) {
        defaultGlobalFeatures().forEach((feature, enable) -> {
            if (enable) {
                builder.featuresToEnable(feature);
            } else {
                builder.featuresToDisable(feature);
            }
        });
    }
}
