package com.itiger.persona.config;

import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author tiger
 */
//@Configuration
public class QuartzConfig {

    /**
     * create quartz scheduler
     */
//    @Bean(name = "quartzScheduler")
    public Scheduler quartzScheduler() throws SchedulerException {
        Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
        scheduler.start();
        return scheduler;
    }
}