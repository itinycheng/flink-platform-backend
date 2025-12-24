package com.flink.platform.web;

import com.flink.platform.web.lifecycle.AppRunner;
import com.flink.platform.web.lifecycle.SystemInfoLogger;
import com.flink.platform.web.lifecycle.WorkerHeartbeat;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** starter. */
@EnableResilientMethods
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT1H")
@EnableTransactionManagement
@ComponentScan(value = "com.flink.platform")
@SpringBootApplication
public class PlatformWebApplication {

    public static void main(String[] args) {
        var application = new SpringApplication(PlatformWebApplication.class);
        application.addListeners(
                (ApplicationListener<ContextClosedEvent>) event -> AppRunner.stop(),
                (ApplicationListener<ContextRefreshedEvent>) event -> contextRefreshed());
        application.run(args);
    }

    public static void contextRefreshed() {
        SystemInfoLogger.logDetails();
        WorkerHeartbeat.Scheduler.start();
    }
}
