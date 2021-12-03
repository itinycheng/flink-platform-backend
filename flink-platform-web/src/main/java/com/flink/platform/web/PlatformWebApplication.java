package com.flink.platform.web;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** starter. */
@EnableCaching
@EnableScheduling
@EnableTransactionManagement
@ComponentScan(value = "com.flink.platform")
@SpringBootApplication(exclude = DruidDataSourceAutoConfigure.class)
public class PlatformWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformWebApplication.class, args);
    }
}
