package com.flink.platform.dao;

import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import com.flink.platform.common.util.JsonUtil;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** Hello world! */
@Configuration
@MapperScan("com.flink.platform.dao.mapper")
public class MapperInitializer {

    static {
        JsonMapper mapper = JsonUtil.jacksonBuilderWithGlobalConfigs().build();
        Jackson3TypeHandler.setObjectMapper(mapper);
    }
}
