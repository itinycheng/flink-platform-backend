package com.flink.platform.dao;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** Hello world! */
@Configuration
@MapperScan("com.flink.platform.dao.mapper")
public class MapperInitializer {}
