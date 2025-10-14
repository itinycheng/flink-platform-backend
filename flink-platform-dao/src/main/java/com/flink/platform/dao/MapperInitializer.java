package com.flink.platform.dao;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.flink.platform.common.util.JsonUtil;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** Hello world! */
@Configuration
@MapperScan("com.flink.platform.dao.mapper")
public class MapperInitializer {

    static {
        JsonMapper mapper = JsonUtil.jacksonBuilderWithGlobalConfigs().build();
        JacksonTypeHandler.setObjectMapper(mapper);
    }
}
