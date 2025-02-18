package com.flink.platform.dao;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flink.platform.common.util.JsonUtil;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** Hello world! */
@Configuration
@MapperScan("com.flink.platform.dao.mapper")
public class MapperInitializer {

    static {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonUtil.addGlobalConfig(objectMapper);
        JacksonTypeHandler.setObjectMapper(objectMapper);
    }
}
