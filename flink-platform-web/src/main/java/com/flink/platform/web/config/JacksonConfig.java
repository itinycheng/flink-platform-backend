package com.flink.platform.web.config;

import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.common.util.json.Jackson3Mapper;
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
        if (JsonUtil.MAPPER instanceof Jackson3Mapper jackson) {
            return builder -> jackson.jacksonBuilderWithGlobalConfigs();
        } else {
            throw new RuntimeException("Jackson 3.x not found, please include Jackson 3.x dependency.");
        }
    }
}
