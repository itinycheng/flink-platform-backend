package com.flink.platform.dao;

import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.common.util.json.Jackson3Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** Hello world! */
@Configuration
@MapperScan("com.flink.platform.dao.mapper")
public class MapperInitializer {

    static {
        if (JsonUtil.MAPPER instanceof Jackson3Mapper jackson) {
            Jackson3TypeHandler.setObjectMapper(jackson.getMapper());
        } else {
            throw new RuntimeException("Jackson 3.x not found, please include Jackson 3.x dependency.");
        }
    }
}
