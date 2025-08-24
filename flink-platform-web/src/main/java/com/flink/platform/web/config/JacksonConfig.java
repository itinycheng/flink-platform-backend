package com.flink.platform.web.config;

import com.fasterxml.jackson.databind.Module;
import com.flink.platform.common.util.JsonUtil;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.MapperFeature.PROPAGATE_TRANSIENT_MARKER;
import static com.flink.platform.common.constants.Constant.GLOBAL_TIME_ZONE;

@Configuration
public class JacksonConfig {

    /**
     * Customize the SpringBoot's default Jackson ObjectMapper.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer customizeObjectMapper() {
        return builder -> {
            builder.timeZone(GLOBAL_TIME_ZONE);
            builder.featuresToEnable(PROPAGATE_TRANSIENT_MARKER);
            builder.featuresToDisable(FAIL_ON_UNKNOWN_PROPERTIES);

            // Register global modules.
            List<Module> modules = JsonUtil.defaultGlobalModules();
            builder.modulesToInstall(modules.toArray(new Module[0]));
        };
    }
}
