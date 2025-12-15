package com.flink.platform.web.config;

import com.flink.platform.common.util.JsonUtil;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SuppressWarnings("unused")
@Configuration
public class JacksonConfig {

    /**
     * Customize the SpringBoot's default Jackson ObjectMapper.
     */
    @Bean
    public JsonMapperBuilderCustomizer customizeObjectMapper() {
        return builder -> JsonUtil.jacksonBuilderWithGlobalConfigs();
    }
}
