package com.flink.platform.web;

import com.flink.platform.web.config.AppRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** starter. */
@EnableCaching
@EnableScheduling
@EnableTransactionManagement
@ComponentScan(value = "com.flink.platform")
@SpringBootApplication
public class PlatformWebApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PlatformWebApplication.class);
        application.addListeners((ApplicationListener<ContextClosedEvent>) event -> AppRunner.stop());
        application.run(args);
    }
}
